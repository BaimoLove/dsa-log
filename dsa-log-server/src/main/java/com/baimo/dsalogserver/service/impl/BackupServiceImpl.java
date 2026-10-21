package com.baimo.dsalogserver.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ListConverter;
import com.baimo.dsalogserver.converter.ProblemConverter;
import com.baimo.dsalogserver.converter.ReviewConverter;
import com.baimo.dsalogserver.converter.TagConverter;
import com.baimo.dsalogserver.dto.ProblemTagPair;
import com.baimo.dsalogserver.entity.Problem;
import com.baimo.dsalogserver.entity.ProblemList;
import com.baimo.dsalogserver.entity.ReviewRecord;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemListMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.ReviewRecordMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.BackupService;
import com.baimo.dsalogserver.service.SettingService;
import com.baimo.dsalogserver.vo.BackupVO;
import com.baimo.dsalogserver.vo.ImportResultVO;
import com.baimo.dsalogserver.vo.ListItemVO;
import com.baimo.dsalogserver.vo.ListVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import com.baimo.dsalogserver.vo.TagRow;
import com.baimo.dsalogserver.vo.TagUsageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 备份实现。
 *
 * <p>导入的冲突规则与前端 Dexie 侧完全一致，否则 M8 对拍两边结果不同：
 * <ul>
 *   <li>题目 / 题单：按 id 比 updatedAt，文件更新才覆盖，否则算 skipped</li>
 *   <li>标签：没有 updatedAt，name 或 color 不同就覆盖</li>
 *   <li>关联与流水：INSERT IGNORE，天然幂等</li>
 *   <li>设置：只有 replace 才还原（merge 的语义是合并数据，不动当前偏好）</li>
 * </ul>
 *
 * <p>replace 必须先物理清空：逻辑删会留下 deleted=1 的行继续占着主键，
 * 之后同 id 的行插不进去，看起来像「导入成功但少了数据」。
 */
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemListMapper problemListMapper;
    private final ProblemListItemMapper problemListItemMapper;
    private final TagMapper tagMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final SettingService settingService;
    private final ProblemConverter problemConverter;
    private final ListConverter listConverter;
    private final TagConverter tagConverter;
    private final ReviewConverter reviewConverter;

    // ---------------- 导出 ----------------

    @Override
    public BackupVO exportAll() {
        List<Problem> problems = problemMapper.selectList(
                Wrappers.<Problem>lambdaQuery().orderByAsc(Problem::getId));
        Map<String, List<TagRow>> tagsByProblem = problems.isEmpty() ? Map.of()
                : problemTagMapper.selectByProblemIds(problems.stream().map(Problem::getId).toList())
                .stream().collect(Collectors.groupingBy(TagRow::getProblemId));

        BackupVO payload = new BackupVO();
        payload.setApp("dsa-log");
        payload.setSchemaVersion(SCHEMA_VERSION);
        payload.setExportedAt(System.currentTimeMillis());
        payload.setProblems(problems.stream()
                .map(p -> problemConverter.toVO(p, tagsByProblem.getOrDefault(p.getId(), List.of())))
                .toList());
        payload.setTags(tagMapper.selectWithUsage(null).stream().map(tagConverter::toVO).toList());
        payload.setLists(problemListMapper.selectAllWithProgress().stream().map(listConverter::toVO).toList());
        payload.setListItems(problemListItemMapper.selectAll());
        payload.setReviews(reviewRecordMapper.selectList(
                        Wrappers.<ReviewRecord>lambdaQuery().orderByAsc(ReviewRecord::getReviewedAt))
                .stream().map(reviewConverter::toVO).toList());
        payload.setSettings(settingService.get());
        return payload;
    }

    /**
     * CSV：与前端 serializeCsv 同一套列与格式（含 BOM，Excel 打开中文不乱码）。
     * 时间写 ISO —— 这是唯一不遵守「一律 epoch 毫秒」的地方，因为它不是接口数据。
     */
    @Override
    public String exportCsv() {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("id,title,difficulty,status,tags,source,url,solveCount,reviewStage,"
                + "solvedAt,lastSolvedAt,nextReviewAt,createdAt\r\n");
        for (ProblemVO p : exportAll().getProblems()) {
            sb.append(csvCell(p.getId())).append(',')
                    .append(csvCell(p.getTitle())).append(',')
                    .append(csvCell(p.getDifficulty())).append(',')
                    .append(csvCell(p.getStatus())).append(',')
                    .append(csvCell(p.getTags().stream().map(t -> t.getName()).collect(Collectors.joining(" | ")))).append(',')
                    .append(csvCell(p.getSource())).append(',')
                    .append(csvCell(p.getUrl())).append(',')
                    .append(csvCell(p.getSolveCount())).append(',')
                    .append(csvCell(p.getReviewStage())).append(',')
                    .append(csvCell(csvTime(p.getSolvedAt()))).append(',')
                    .append(csvCell(csvTime(p.getLastSolvedAt()))).append(',')
                    .append(csvCell(csvTime(p.getNextReviewAt()))).append(',')
                    .append(csvCell(csvTime(p.getCreatedAt()))).append("\r\n");
        }
        return sb.toString();
    }

    // ---------------- 导入 ----------------

    @Override
    @Transactional
    public ImportResultVO importBackup(BackupVO payload, String mode, String confirm) {
        if (payload == null) {
            throw BizException.invalid("备份内容不能为空");
        }
        if (payload.getSchemaVersion() == null || payload.getSchemaVersion() != SCHEMA_VERSION) {
            throw new BizException(ErrorCode.IMPORT_INVALID,
                    "备份版本不匹配：文件是 " + payload.getSchemaVersion() + "，当前只支持 " + SCHEMA_VERSION);
        }
        String m = (mode == null || mode.isBlank()) ? "merge" : mode.trim().toLowerCase();
        if (!"merge".equals(m) && !"replace".equals(m)) {
            throw BizException.invalid("mode 只能是 merge 或 replace");
        }
        if ("replace".equals(m) && !"REPLACE".equals(confirm == null ? null : confirm.trim())) {
            throw BizException.invalid("replace 模式必须带 confirm=REPLACE");
        }

        ImportResultVO result = new ImportResultVO();
        result.setMode(m);

        if ("replace".equals(m)) {
            clearAll();
        }

        try {
            importTags(payload.getTags(), result);
            importLists(payload.getLists(), result);
            List<Problem> written = importProblems(payload.getProblems(), result);
            linkTags(payload.getProblems(), written);
            result.setListItems(importListItems(payload.getListItems()));
            result.setReviews(importReviews(payload.getReviews()));
        } catch (DuplicateKeyException e) {
            // 标签名唯一索引撞了：多半是文件里的 id 与库里某条同名标签不同
            throw new BizException(ErrorCode.IMPORT_INVALID, "导入数据存在唯一键冲突（多半是标签重名），请先清理后再试");
        }

        if ("replace".equals(m) && payload.getSettings() != null) {
            settingService.replaceAll(payload.getSettings());
        }
        return result;
    }

    private void clearAll() {
        // 先清关联表再清主表：没有外键，这个顺序只是让回滚时的日志更易读
        problemTagMapper.deleteAll();
        problemListItemMapper.deleteAll();
        reviewRecordMapper.deleteAll();
        problemMapper.physicalDeleteAll();
        problemListMapper.physicalDeleteAll();
        tagMapper.deleteAll();
    }

    private void importTags(List<TagUsageVO> incoming, ImportResultVO result) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        Map<String, Tag> existing = tagMapper.selectList(null).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t, (a, b) -> a));

        List<Tag> toWrite = new ArrayList<>();
        for (TagUsageVO vo : incoming) {
            if (vo == null || isBlank(vo.getId())) {
                continue;
            }
            String name = nvl(vo.getName(), "").trim();
            if (name.isEmpty()) {
                continue;
            }
            String color = isBlank(vo.getColor()) ? "#6366f1" : vo.getColor().trim();
            Tag e = existing.get(vo.getId());
            if (e == null) {
                Tag tag = new Tag();
                tag.setId(vo.getId());
                tag.setName(name);
                tag.setColor(color);
                tag.setCreatedAt(TimeUtil.fromMillis(vo.getCreatedAt()) == null
                        ? TimeUtil.now() : TimeUtil.fromMillis(vo.getCreatedAt()));
                toWrite.add(tag);
                result.getTags().created();
            } else if (Objects.equals(e.getName(), name) && Objects.equals(e.getColor(), color)) {
                result.getTags().skipped();
            } else {
                Tag tag = new Tag();
                tag.setId(vo.getId());
                tag.setName(name);
                tag.setColor(color);
                tag.setCreatedAt(e.getCreatedAt());
                toWrite.add(tag);
                result.getTags().updated();
            }
        }
        if (!toWrite.isEmpty()) {
            tagMapper.upsertBatch(toWrite);
        }
    }

    private void importLists(List<ListVO> incoming, ImportResultVO result) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        Map<String, ProblemList> existing = problemListMapper.selectAllIncludingDeleted().stream()
                .collect(Collectors.toMap(ProblemList::getId, l -> l, (a, b) -> a));

        List<ProblemList> toWrite = new ArrayList<>();
        for (ListVO vo : incoming) {
            if (vo == null || isBlank(vo.getId())) {
                continue;
            }
            String name = nvl(vo.getName(), "").trim();
            if (name.isEmpty()) {
                continue;
            }
            ProblemList e = existing.get(vo.getId());
            ProblemList row = new ProblemList();
            row.setId(vo.getId());
            row.setName(name);
            row.setDescription(nvl(vo.getDescription(), ""));
            row.setColor(isBlank(vo.getColor()) ? "#6366f1" : vo.getColor().trim());
            row.setSortOrder(vo.getSortOrder() == null ? 0 : vo.getSortOrder());
            row.setCreatedAt(TimeUtil.fromMillis(vo.getCreatedAt()));
            row.setUpdatedAt(TimeUtil.fromMillis(vo.getUpdatedAt()));
            row.setDeleted(0);
            if (row.getCreatedAt() == null) {
                row.setCreatedAt(TimeUtil.now());
            }
            if (row.getUpdatedAt() == null) {
                row.setUpdatedAt(row.getCreatedAt());
            }

            if (e == null) {
                toWrite.add(row);
                result.getLists().created();
            } else if (millis(e.getUpdatedAt()) >= millis(row.getUpdatedAt())) {
                result.getLists().skipped();
            } else {
                toWrite.add(row);
                result.getLists().updated();
            }
        }
        if (!toWrite.isEmpty()) {
            problemListMapper.upsertBatch(toWrite);
        }
    }

    /** 返回真正写入（新增或更新）的题目，用于决定要不要重建标签关联 */
    private List<Problem> importProblems(List<ProblemVO> incoming, ImportResultVO result) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        Map<String, Problem> existing = problemMapper.selectAllIncludingDeleted().stream()
                .collect(Collectors.toMap(Problem::getId, p -> p, (a, b) -> a));

        List<Problem> written = new ArrayList<>();
        List<Problem> toWrite = new ArrayList<>();
        for (ProblemVO vo : incoming) {
            if (vo == null || isBlank(vo.getId())) {
                continue;
            }
            Problem row = toEntity(vo);
            Problem e = existing.get(row.getId());
            if (e == null) {
                toWrite.add(row);
                written.add(row);
                result.getProblems().created();
            } else if (millis(e.getUpdatedAt()) >= millis(row.getUpdatedAt())) {
                result.getProblems().skipped();
            } else {
                toWrite.add(row);
                written.add(row);
                result.getProblems().updated();
            }
        }
        if (!toWrite.isEmpty()) {
            problemMapper.upsertBatch(toWrite);
        }
        return written;
    }

    /**
     * 重建 problem_tag。只对真正写进去的题做，被跳过的题以库里的关联为准；
     * 用 INSERT IGNORE，所以重复导入不会产生重复行。
     */
    private void linkTags(List<ProblemVO> incoming, List<Problem> written) {
        if (written.isEmpty() || incoming == null) {
            return;
        }
        Set<String> writtenIds = written.stream().map(Problem::getId).collect(Collectors.toCollection(HashSet::new));
        List<ProblemTagPair> pairs = new ArrayList<>();
        for (ProblemVO vo : incoming) {
            if (vo == null || vo.getId() == null || !writtenIds.contains(vo.getId()) || vo.getTagIds() == null) {
                continue;
            }
            vo.getTagIds().stream().filter(id -> !isBlank(id))
                    .forEach(tagId -> pairs.add(new ProblemTagPair(vo.getId(), tagId)));
        }
        if (!pairs.isEmpty()) {
            problemTagMapper.insertIgnorePairs(pairs);
        }
    }

    private int importListItems(List<ListItemVO> incoming) {
        if (incoming == null) {
            return 0;
        }
        List<ListItemVO> clean = incoming.stream()
                .filter(i -> i != null && !isBlank(i.getListId()) && !isBlank(i.getProblemId()))
                .peek(i -> {
                    if (i.getSortOrder() == null) {
                        i.setSortOrder(0);
                    }
                })
                .toList();
        if (clean.isEmpty()) {
            return 0;
        }
        return problemListItemMapper.insertIgnoreFullBatch(clean);
    }

    private int importReviews(List<com.baimo.dsalogserver.vo.ReviewRecordVO> incoming) {
        if (incoming == null) {
            return 0;
        }
        List<ReviewRecord> rows = new ArrayList<>();
        for (com.baimo.dsalogserver.vo.ReviewRecordVO vo : incoming) {
            if (vo == null || isBlank(vo.getId()) || isBlank(vo.getProblemId()) || isBlank(vo.getKind())) {
                continue;
            }
            ReviewRecord r = new ReviewRecord();
            r.setId(vo.getId());
            r.setProblemId(vo.getProblemId());
            r.setKind(vo.getKind());
            r.setRating(vo.getRating());
            r.setFromStage(vo.getFromStage());
            r.setToStage(vo.getToStage());
            r.setIntervalDays(vo.getIntervalDays());
            r.setNextReviewAt(TimeUtil.fromMillis(vo.getNextReviewAt()));
            r.setReviewedAt(TimeUtil.fromMillis(vo.getReviewedAt()) == null
                    ? TimeUtil.now() : TimeUtil.fromMillis(vo.getReviewedAt()));
            r.setCreatedAt(r.getReviewedAt());
            rows.add(r);
        }
        if (rows.isEmpty()) {
            return 0;
        }
        return reviewRecordMapper.insertIgnoreBatch(rows);
    }

    // ---------------- 内部 ----------------

    private Problem toEntity(ProblemVO vo) {
        LocalDateTime now = TimeUtil.now();
        Problem p = new Problem();
        p.setId(vo.getId());
        p.setTitle(isBlank(vo.getTitle()) ? "未命名" : vo.getTitle().trim());
        p.setDifficulty(Set.of("easy", "medium", "hard").contains(vo.getDifficulty())
                ? vo.getDifficulty() : "medium");
        p.setStatus("solved".equals(vo.getStatus()) ? "solved" : "todo");
        p.setApproach(nvl(vo.getApproach(), ""));
        p.setNote(nvl(vo.getNote(), ""));
        p.setSource(nvl(vo.getSource(), ""));
        p.setUrl(nvl(vo.getUrl(), ""));
        p.setSolvedAt(TimeUtil.fromMillis(vo.getSolvedAt()));
        p.setLastSolvedAt(TimeUtil.fromMillis(vo.getLastSolvedAt()));
        p.setSolveCount(vo.getSolveCount() == null ? 0 : Math.max(0, vo.getSolveCount()));
        p.setReviewStage(vo.getReviewStage() == null ? 0 : Math.min(4, Math.max(0, vo.getReviewStage())));
        p.setNextReviewAt(TimeUtil.fromMillis(vo.getNextReviewAt()));
        p.setLastReviewedAt(TimeUtil.fromMillis(vo.getLastReviewedAt()));
        p.setCreatedAt(TimeUtil.fromMillis(vo.getCreatedAt()) == null ? now : TimeUtil.fromMillis(vo.getCreatedAt()));
        p.setUpdatedAt(TimeUtil.fromMillis(vo.getUpdatedAt()) == null ? p.getCreatedAt() : TimeUtil.fromMillis(vo.getUpdatedAt()));
        // 导出的都是存活行；导入一律当活行处理（replace 已经把库清空）
        p.setDeleted(0);
        return p;
    }

    private static long millis(LocalDateTime t) {
        Long v = TimeUtil.toMillis(t);
        return v == null ? 0L : v;
    }

    private static String csvTime(Long ms) {
        LocalDateTime t = TimeUtil.fromMillis(ms);
        return t == null ? "" : t.format(CSV_TIME);
    }

    private static String csvCell(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        return s.matches(".*[\",\\r\\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nvl(String s, String fallback) {
        return s == null ? fallback : s;
    }
}

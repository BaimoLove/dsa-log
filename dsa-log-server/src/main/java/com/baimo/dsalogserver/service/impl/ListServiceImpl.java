package com.baimo.dsalogserver.service.impl;

import com.baimo.dsalogserver.common.BatchIds;
import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.common.PageResult;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.ListConverter;
import com.baimo.dsalogserver.dto.CreateListRequest;
import com.baimo.dsalogserver.dto.ProblemQuery;
import com.baimo.dsalogserver.dto.UpdateListRequest;
import com.baimo.dsalogserver.entity.ProblemList;
import com.baimo.dsalogserver.mapper.ProblemListItemMapper;
import com.baimo.dsalogserver.mapper.ProblemListMapper;
import com.baimo.dsalogserver.mapper.ProblemMapper;
import com.baimo.dsalogserver.service.ListService;
import com.baimo.dsalogserver.service.ProblemService;
import com.baimo.dsalogserver.vo.BatchVO;
import com.baimo.dsalogserver.vo.ListRow;
import com.baimo.dsalogserver.vo.ListVO;
import com.baimo.dsalogserver.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 题单业务实现。
 *
 * <p>进度不落库、不缓存：所有返回 ListVO 的方法都从同一条进度 SQL 取数，
 * 因此「看板 12/30」与「题单内 status=solved 的 total」不会漂。新建题单是唯一例外，
 * 它的进度必然是 0/0，省掉一次查询。
 *
 * <p>「加入题目」只有这一份实现，批量端点 {@code POST /api/problems/bulk/lists}
 * 也走这里，避免同一件事有两套 affected 口径。
 */
@Service
@RequiredArgsConstructor
public class ListServiceImpl implements ListService {

    /** 与前端色板第一项一致 */
    private static final String DEFAULT_COLOR = "#6366f1";

    /** 只接受 #RGB / #RRGGBB：色值会直接进 SVG stroke，不接受任意字符串 */
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

    private static final int MAX_NAME = 64;
    private static final int MAX_DESCRIPTION = 512;

    private final ProblemListMapper problemListMapper;
    private final ProblemListItemMapper problemListItemMapper;
    private final ProblemMapper problemMapper;
    private final ProblemService problemService;
    private final ListConverter converter;

    @Override
    public List<ListVO> list() {
        return problemListMapper.selectAllWithProgress().stream().map(converter::toVO).toList();
    }

    @Override
    public ListVO get(String id) {
        ListRow row = problemListMapper.selectOneWithProgress(id);
        if (row == null) {
            throw BizException.notFound("题单");
        }
        return converter.toVO(row);
    }

    @Override
    @Transactional
    public ListVO create(CreateListRequest req) {
        String name = requireName(req.getName());

        LocalDateTime now = TimeUtil.now();
        ProblemList row = new ProblemList();
        row.setId(UUID.randomUUID().toString());
        row.setName(name);
        row.setDescription(trimToEmpty(req.getDescription()));
        row.setColor(requireColor(req.getColor()));
        // 单用户应用，不为此加行锁：并发新建撞号只会让两张卡顺序并列，不影响正确性
        row.setSortOrder(problemListMapper.nextSortOrder());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setDeleted(0);
        problemListMapper.insert(row);

        // 新题单没有任何关联，进度必然是 0/0，不必再查一次
        return converter.toVO(row);
    }

    @Override
    @Transactional
    public ListVO update(String id, UpdateListRequest req) {
        ProblemList row = requireRow(id);
        boolean changed = false;
        if (req.getName() != null) {
            row.setName(requireName(req.getName()));
            changed = true;
        }
        if (req.getDescription() != null) {
            row.setDescription(trimToEmpty(req.getDescription()));
            changed = true;
        }
        if (req.getColor() != null) {
            row.setColor(requireColor(req.getColor()));
            changed = true;
        }
        if (changed) {
            row.setUpdatedAt(TimeUtil.now());
            problemListMapper.updateById(row);
        }
        // 走 get 而不是直接用内存行：进度要现算，不能拿旧值糊过去
        return get(id);
    }

    @Override
    @Transactional
    public void remove(String id) {
        requireRow(id);
        // 题单逻辑删，关联行物理删；题目本身不受影响
        problemListMapper.deleteById(id);
        problemListItemMapper.deleteByListId(id);
    }

    @Override
    public PageResult<ProblemVO> problems(String listId, ProblemQuery query) {
        if (problemListMapper.countAlive(listId) == 0) {
            throw BizException.notFound("题单");
        }
        ProblemQuery q = query == null ? new ProblemQuery() : query;
        // 路径参数优先：query 里带的 listId 一律覆盖，否则前端漏清参数就能查到别的题单
        q.setListId(listId);
        return problemService.list(q);
    }

    @Override
    @Transactional
    public BatchVO addProblems(String listId, List<String> problemIds) {
        String target = requireAlive(listId);
        List<String> ids = BatchIds.normalize(problemIds);

        // 两条 SQL 都只用来做集合判断，**不拿它们的返回顺序**：
        // MySQL 没有 ORDER BY 时返回顺序由执行计划决定，如果直接拿 selectMissingIds
        // 的结果去分配 sort_order，题单里的先后就变成随机的（M8 对拍发现的差异）。
        // 这里按入参顺序过滤，与 Dexie 侧「按 targets 顺序依次 put」保持一致。
        Set<String> alive = new HashSet<>(problemMapper.selectExistingIds(ids));
        if (alive.isEmpty()) {
            return new BatchVO(0);
        }
        Set<String> missing = new HashSet<>(problemListItemMapper.selectMissingIds(target, ids));
        List<String> ordered = ids.stream()
                .filter(id -> alive.contains(id) && missing.contains(id))
                .toList();
        if (ordered.isEmpty()) {
            return new BatchVO(0);
        }
        int startOrder = problemListItemMapper.nextSortOrder(target);
        problemListItemMapper.insertIgnoreBatch(target, ordered, startOrder);
        return new BatchVO(ordered.size());
    }

    @Override
    @Transactional
    public void removeProblem(String listId, String problemId) {
        requireAlive(listId);
        String pid = problemId == null ? "" : problemId.trim();
        if (pid.isEmpty()) {
            throw BizException.invalid("problemId 不能为空");
        }
        if (problemListItemMapper.deleteOne(listId, pid) == 0) {
            // 直接给完整消息：notFound(what) 会拼成「该题不在此题单内不存在」
            throw new BizException(ErrorCode.NOT_FOUND, "该题不在此题单内");
        }
    }

    /**
     * 题单成员 id 列表。刻意不校验题单是否存在、也不过滤归档：
     * Dexie 侧就是「关联表里有什么就返回什么」，题单不存在得到空数组而不是 404。
     */
    @Override
    public List<String> problemIds(String listId) {
        if (listId == null || listId.trim().isEmpty()) {
            return List.of();
        }
        return problemListItemMapper.selectProblemIds(listId.trim());
    }

    // ---------------- 内部 ----------------

    private ProblemList requireRow(String id) {
        ProblemList row = id == null ? null : problemListMapper.selectById(id);
        if (row == null) {
            throw BizException.notFound("题单");
        }
        return row;
    }

    private String requireAlive(String listId) {
        String id = listId == null ? "" : listId.trim();
        if (id.isEmpty() || problemListMapper.countAlive(id) == 0) {
            throw BizException.notFound("题单");
        }
        return id;
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw BizException.invalid("题单名不能为空");
        }
        if (name.length() > MAX_NAME) {
            throw BizException.invalid("题单名不能超过 " + MAX_NAME + " 个字符");
        }
        return name;
    }

    private static String requireColor(String raw) {
        String color = raw == null ? "" : raw.trim();
        if (color.isEmpty()) {
            return DEFAULT_COLOR;
        }
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw BizException.invalid("颜色值必须是 #RGB 或 #RRGGBB 形式");
        }
        return color;
    }

    private static String trimToEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }
}

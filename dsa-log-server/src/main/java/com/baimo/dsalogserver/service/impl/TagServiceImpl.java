package com.baimo.dsalogserver.service.impl;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.TagConverter;
import com.baimo.dsalogserver.dto.CreateTagRequest;
import com.baimo.dsalogserver.dto.UpdateTagRequest;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.TagService;
import com.baimo.dsalogserver.vo.TagUsageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 标签业务实现。
 *
 * <p>重名判定交给 SQL 的 {@code name = #{name}}：库的排序规则是
 * utf8mb4_general_ci，天然大小写不敏感，与前端 Dexie 侧用 nameLower 唯一索引
 * 的语义一致。不要在这里转小写再比，那会让「唯一索引」和「业务判断」两套口径。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private static final String DEFAULT_COLOR = "#6366f1";
    private static final int MAX_NAME = 32;
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

    private final TagMapper tagMapper;
    private final ProblemTagMapper problemTagMapper;
    private final TagConverter converter;

    @Override
    public List<TagUsageVO> list(String keyword) {
        return tagMapper.selectWithUsage(trim(keyword)).stream().map(converter::toVO).toList();
    }

    @Override
    @Transactional
    public TagUsageVO create(CreateTagRequest req) {
        String name = requireName(req.getName());
        assertNameFree(name, null);

        LocalDateTime now = TimeUtil.now();
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID().toString());
        tag.setName(name);
        tag.setColor(resolveColor(req.getColor()));
        tag.setCreatedAt(now);
        tagMapper.insert(tag);

        return converter.toVO(tagMapper.selectUsageById(tag.getId()));
    }

    @Override
    @Transactional
    public TagUsageVO update(String id, UpdateTagRequest req) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw BizException.notFound("标签");
        }
        if (req.getName() != null) {
            String name = requireName(req.getName());
            assertNameFree(name, id);
            tag.setName(name);
        }
        if (req.getColor() != null) {
            tag.setColor(resolveColor(req.getColor()));
        }
        tagMapper.updateById(tag);

        return converter.toVO(tagMapper.selectUsageById(id));
    }

    @Override
    @Transactional
    public void remove(String id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw BizException.notFound("标签");
        }
        // 关联表没有逻辑删，物理清掉；题目本身不动
        problemTagMapper.deleteByTagId(id);
        tagMapper.deleteById(id);
    }

    // ---------------- 内部 ----------------


    private String requireName(String raw) {
        String name = trim(raw);
        if (name == null || name.isEmpty()) {
            throw BizException.invalid("标签名不能为空");
        }
        if (name.length() > MAX_NAME) {
            throw BizException.invalid("标签名不能超过 " + MAX_NAME + " 个字符");
        }
        return name;
    }

    private void assertNameFree(String name, String excludeId) {
        if (tagMapper.countByName(name, excludeId) > 0) {
            throw BizException.conflict("标签「" + name + "」已存在");
        }
    }

    /** 颜色缺省用色板第一项；非法值直接 400，因为它会进 SVG 的 stroke */
    private String resolveColor(String raw) {
        String color = trim(raw);
        if (color == null || color.isEmpty()) {
            return DEFAULT_COLOR;
        }
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw BizException.invalid("颜色只接受 #RGB 或 #RRGGBB");
        }
        return color;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}

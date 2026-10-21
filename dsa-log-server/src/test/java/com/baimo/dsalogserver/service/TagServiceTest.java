package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.common.BizException;
import com.baimo.dsalogserver.common.ErrorCode;
import com.baimo.dsalogserver.common.TimeUtil;
import com.baimo.dsalogserver.converter.TagConverter;
import com.baimo.dsalogserver.dto.CreateTagRequest;
import com.baimo.dsalogserver.dto.UpdateTagRequest;
import com.baimo.dsalogserver.entity.Tag;
import com.baimo.dsalogserver.mapper.ProblemTagMapper;
import com.baimo.dsalogserver.mapper.TagMapper;
import com.baimo.dsalogserver.service.impl.TagServiceImpl;
import com.baimo.dsalogserver.vo.TagUsageRow;
import com.baimo.dsalogserver.vo.TagUsageVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签业务契约：重名返 409（不是 400）、颜色只收 hex、删除先清关联。
 * SQL 的正确性由 curl 实测覆盖，这里只钉业务判断。
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagMapper tagMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Spy
    private TagConverter converter = new TagConverter();

    @InjectMocks
    private TagServiceImpl service;

    private static TagUsageRow row(String id, String name, long usage) {
        TagUsageRow r = new TagUsageRow();
        r.setId(id);
        r.setName(name);
        r.setColor("#6366f1");
        r.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 0, 0));
        r.setUsageCount(usage);
        return r;
    }

    @Test
    @DisplayName("列表：时间转 epoch 毫秒，usageCount 原样带出")
    void list() {
        when(tagMapper.selectWithUsage("dp")).thenReturn(List.of(row("t1", "DP", 7)));

        List<TagUsageVO> list = service.list("  dp  ");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getUsageCount()).isEqualTo(7);
        // 时间统一走 TimeUtil，避免在测试里手算时区偏移
        assertThat(list.get(0).getCreatedAt())
                .isEqualTo(TimeUtil.toMillis(LocalDateTime.of(2026, 10, 21, 10, 0, 0)));
    }

    @Test
    @DisplayName("新建：名字去空格，颜色缺省 #6366f1")
    void createDefaults() {
        CreateTagRequest req = new CreateTagRequest();
        req.setName("  动态规划  ");
        req.setColor("  ");
        when(tagMapper.countByName("动态规划", null)).thenReturn(0);
        when(tagMapper.selectUsageById(anyString())).thenReturn(row("t1", "动态规划", 0));

        TagUsageVO vo = service.create(req);
        assertThat(vo.getName()).isEqualTo("动态规划");

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).insert(captor.capture());
        assertThat(captor.getValue().getColor()).isEqualTo("#6366f1");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("新建：空名 400、超长 400、重名 409、非法颜色 400")
    void createValidation() {
        CreateTagRequest blank = new CreateTagRequest();
        blank.setName("   ");
        assertThatThrownBy(() -> service.create(blank))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        CreateTagRequest tooLong = new CreateTagRequest();
        tooLong.setName("x".repeat(33));
        assertThatThrownBy(() -> service.create(tooLong))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        CreateTagRequest dup = new CreateTagRequest();
        dup.setName("DP");
        when(tagMapper.countByName("DP", null)).thenReturn(1);
        assertThatThrownBy(() -> service.create(dup))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        CreateTagRequest badColor = new CreateTagRequest();
        badColor.setName("图论");
        badColor.setColor("red");
        when(tagMapper.countByName("图论", null)).thenReturn(0);
        assertThatThrownBy(() -> service.create(badColor))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    @DisplayName("改名：重名校验排除自己，只传颜色时不动名字")
    void update() {
        Tag tag = new Tag();
        tag.setId("t1");
        tag.setName("DP");
        tag.setColor("#6366f1");
        when(tagMapper.selectById("t1")).thenReturn(tag);
        when(tagMapper.countByName("动态规划", "t1")).thenReturn(0);
        when(tagMapper.selectUsageById("t1")).thenReturn(row("t1", "动态规划", 3));

        UpdateTagRequest req = new UpdateTagRequest();
        req.setName("动态规划");
        service.update("t1", req);
        verify(tagMapper).countByName("动态规划", "t1");

        UpdateTagRequest colorOnly = new UpdateTagRequest();
        colorOnly.setColor("#0ea5e9");
        service.update("t1", colorOnly);
        assertThat(tag.getName()).isEqualTo("动态规划");
        assertThat(tag.getColor()).isEqualTo("#0ea5e9");
    }

    @Test
    @DisplayName("改名撞到别的标签返 409")
    void updateConflict() {
        Tag tag = new Tag();
        tag.setId("t1");
        tag.setName("DP");
        when(tagMapper.selectById("t1")).thenReturn(tag);
        when(tagMapper.countByName("图论", "t1")).thenReturn(1);

        UpdateTagRequest req = new UpdateTagRequest();
        req.setName("图论");
        assertThatThrownBy(() -> service.update("t1", req))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("删除：先清 problem_tag 再删标签；不存在返 404")
    void remove() {
        Tag tag = new Tag();
        tag.setId("t1");
        when(tagMapper.selectById("t1")).thenReturn(tag);

        service.remove("t1");
        verify(problemTagMapper).deleteByTagId("t1");
        verify(tagMapper).deleteById("t1");

        when(tagMapper.selectById("nope")).thenReturn(null);
        assertThatThrownBy(() -> service.remove("nope"))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(tagMapper, never()).deleteById(eq("nope"));
    }
}

package com.baimo.dsalogserver.vo;

import lombok.Data;

/**
 * 导入结果，与前端 ImportResult 一一对应。
 *
 * <p>skipped 的语义是「文件里的版本不比库里新」，不是「格式不对」——
 * 格式不对整份请求就 422 了，不会走到这里。
 */
@Data
public class ImportResultVO {

    @Data
    public static class Counts {
        private int created;
        private int updated;
        private int skipped;

        public void created() {
            created++;
        }

        public void updated() {
            updated++;
        }

        public void skipped() {
            skipped++;
        }
    }

    private String mode;
    private Counts problems = new Counts();
    private Counts tags = new Counts();
    private Counts lists = new Counts();
    private int listItems;
    private int reviews;
}

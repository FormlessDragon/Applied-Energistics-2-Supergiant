package ae2.crafting;

import ae2.api.stacks.AEKey;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

class CraftingPerformanceText {
    private final boolean chinese;

    private CraftingPerformanceText(boolean chinese) {
        this.chinese = chinese;
    }

    static CraftingPerformanceText localized() {
        var language = Locale.getDefault().getLanguage();
        return new CraftingPerformanceText("zh".equalsIgnoreCase(language));
    }

    static CraftingPerformanceText bilingual() {
        return new CraftingPerformanceText(false) {
            @Override
            String start(AEKey output, long amount) {
                return "start output=" + output + " amount=" + amount
                    + " | 开始 输出=" + output + " 数量=" + amount;
            }

            @Override
            String stage(String name, long nanos) {
                var micros = TimeUnit.NANOSECONDS.toMicros(nanos);
                return "stage=" + name + " time=" + micros + " us"
                    + " | 阶段=" + name + " 耗时=" + micros + " 微秒";
            }

            @Override
            String count(String name, long amount) {
                return "count=" + name + " Value=" + amount
                    + " | 计数=" + name + " 值=" + amount;
            }

            @Override
            String summary(AEKey output, long amount, long nanos, CraftingCalculation calculation, long stages) {
                var micros = TimeUnit.NANOSECONDS.toMicros(nanos);
                return "summary output=" + output + " amount=" + amount
                    + " time=" + micros + " us"
                    + " treeDepth=" + calculation.getTreeDepth()
                    + " treeNodes=" + calculation.getTreeNodeCount()
                    + " patternNodes=" + calculation.getPatternNodeCount()
                    + " maxRequestDepth=" + calculation.getMaxRequestDepth()
                    + " stages=" + stages
                    + " | 汇总 输出=" + output + " 数量=" + amount
                    + " 耗时=" + micros + " 微秒"
                    + " 合成树深度=" + calculation.getTreeDepth()
                    + " 节点数=" + calculation.getTreeNodeCount()
                    + " 样板节点数=" + calculation.getPatternNodeCount()
                    + " 最大请求深度=" + calculation.getMaxRequestDepth()
                    + " 阶段数=" + stages;
            }

            @Override
            String hotspot(String name, long nanos, long count) {
                var micros = TimeUnit.NANOSECONDS.toMicros(nanos);
                return "hotspot stage=" + name + " total=" + micros + " us count=" + count
                    + " | 热点 阶段=" + name + " 总耗时=" + micros + " 微秒 次数=" + count;
            }
        };
    }

    String start(AEKey output, long amount) {
        if (chinese) {
            return "开始 输出=" + output + " 数量=" + amount;
        }
        return "start output=" + output + " amount=" + amount;
    }

    String stage(String name, long nanos) {
        var micros = TimeUnit.NANOSECONDS.toMicros(nanos);
        if (chinese) {
            return "阶段=" + name + " 耗时=" + micros + " 微秒";
        }
        return "stage=" + name + " time=" + micros + " us";
    }

    String count(String name, long amount) {
        if (chinese) {
            return "计数=" + name + " 值=" + amount;
        }
        return "count=" + name + " Value=" + amount;
    }

    String summary(AEKey output, long amount, long nanos, CraftingCalculation calculation, long stages) {
        var micros = TimeUnit.NANOSECONDS.toMicros(nanos);
        if (chinese) {
            return "汇总 输出=" + output + " 数量=" + amount
                + " 耗时=" + micros + " 微秒"
                + " 合成树深度=" + calculation.getTreeDepth()
                + " 节点数=" + calculation.getTreeNodeCount()
                + " 样板节点数=" + calculation.getPatternNodeCount()
                + " 最大请求深度=" + calculation.getMaxRequestDepth()
                + " 阶段数=" + stages;
        }
        return "summary output=" + output + " amount=" + amount
            + " time=" + micros + " us"
            + " treeDepth=" + calculation.getTreeDepth()
            + " treeNodes=" + calculation.getTreeNodeCount()
            + " patternNodes=" + calculation.getPatternNodeCount()
            + " maxRequestDepth=" + calculation.getMaxRequestDepth()
            + " stages=" + stages;
    }

    String hotspot(String name, long nanos, long count) {
        var micros = TimeUnit.NANOSECONDS.toMicros(nanos);
        if (chinese) {
            return "热点 阶段=" + name + " 总耗时=" + micros + " 微秒 次数=" + count;
        }
        return "hotspot stage=" + name + " total=" + micros + " us count=" + count;
    }
}

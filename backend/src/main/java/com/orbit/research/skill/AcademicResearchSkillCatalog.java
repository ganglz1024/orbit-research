package com.orbit.research.skill;

import java.util.List;
import java.util.Optional;

/** Orbit's model-only adaptations of the Academic Research Skills workflows. */
public final class AcademicResearchSkillCatalog {
    private AcademicResearchSkillCatalog() {}

    public record Spec(String id, String name, String description, String instructions) {}

    private static final String SCOPE = "这是 Academic Research Skills 的 Orbit 对话适配流程。只使用本次明确提供的用户文字、论文题录与 PDF 片段。"
        + "不能声称已经运行原项目的 Claude Code 代理、脚本、联网检索、引文数据库、PRISMA 统计、文件生成或独立复核。"
        + "先说明实际资料范围；区分原文事实、模型归纳与待验证建议；缺少依据时明确写待核验，不编造引用、页码或实验结果。";

    private static final List<Spec> ITEMS = List.of(
        new Spec("ars_research", "ARS 深度研究", "研究问题、检索策略和已提供材料的证据梳理；Orbit 对话适配，不执行联网检索。",
            SCOPE + "围绕用户问题输出：可检验的研究问题、关键概念、可复制的中英文检索式、已提供材料的证据矩阵、相互矛盾或缺失的证据、下一步人工核验。"
            + "若未提供文献，检索式只是建议，不得列出声称已检索到的论文。系统综述或元分析请求只能先协助制定方案，不能伪造筛选量或效应量。"),
        new Spec("ars_write", "ARS 学术写作", "根据明确提供的资料组织论文提纲、章节草稿和修订建议；不自动生成 DOCX/PDF。",
            SCOPE + "先确认论文类型、目标读者和用户已有素材。按用户目标给出论证提纲或所请求章节的可编辑草稿，并标明每个核心论断所需证据。"
            + "仅对已提供且可核验的文献做具体引用；缺来源使用【待补来源】。不得编造方法、数据、结果或投稿状态；写作决策留给作者。"),
        new Spec("ars_review", "ARS 同行评阅", "对用户提供的论文草稿做有证据的模拟评阅；不冒充真实期刊审稿。",
            SCOPE + "对明确提供的稿件或片段，从问题价值、方法、证据、论证和表达提出主要问题与次要问题。"
            + "每条意见引用可见的具体文字或章节；分别写出影响、可操作修订建议及仍需材料。若只有摘要或题录，限定评阅范围。"
            + "这是单模型辅助评阅，不是独立审稿人投票或期刊录用决定。"),
        new Spec("ars_pipeline", "ARS 研究流程", "规划从研究问题到稿件修订的分阶段路线；每阶段由用户决定是否继续。",
            SCOPE + "根据用户当前进度，列出当前阶段、已经具备的材料、下一阶段交付物和人工确认点。"
            + "建议覆盖问题界定、文献证据、研究设计、写作、证据核对、模拟评阅、修订与终检。"
            + "一次只推进用户请求的阶段；不能声称自动调度其他 Skill、运行完整流水线或完成未实际执行的质量门。"),
        new Spec("ars_screen", "ARS 文献筛选", "按用户确认的纳排标准辅助评估明确提供的记录；不自动执行双盲筛选与 PRISMA。",
            SCOPE + "先检查研究问题、纳入标准、排除标准和资料字段是否充分；标准未确认时先给可编辑方案，不直接筛选。"
            + "对本次明确提供的每条记录给出候选纳入、候选排除或待确认，并引用题名、摘要或全文片段中的依据及排除规则。"
            + "缺字段时保持待确认；不虚构第二位独立评阅者、去重结果、PRISMA 数量或最终综述结论。")
    );

    public static List<Spec> all() { return ITEMS; }
    public static Optional<Spec> find(String id) { return ITEMS.stream().filter(item -> item.id().equals(id)).findFirst(); }
    public static boolean contains(String id) { return find(id).isPresent(); }
}

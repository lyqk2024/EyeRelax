package com.eye.tutorial;

import javax.swing.JComponent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** One catalogue supplies help, optional courses and genuine feature-update offers. */
public final class FeatureTutorials {
 public record Topic(String id,String name,String icon,String prompt,List<String[]> copy){}
 private FeatureTutorials(){}
 private static Topic topic(String id,String name,String icon,String prompt,String[]... copy){return new Topic(id,name,icon,prompt,List.of(copy));}
 private static final List<Topic> TOPICS=List.of(
  topic("plans","计划管理","calendar","了解计划管理？",
   new String[]{"today","今日任务列出今天可执行的计划，周期计划会按规则重复出现。"},
   new String[]{"add","新建计划时，填写名称、总时长和执行日期。"},
   new String[]{"more","创建计划后，卡片右侧的更多菜单可置顶、保存模板或删除。"},
   new String[]{"scheduled","预定任务查看未来安排；过去计划保留已经投入的记录。"}),
  topic("archive","归档与模板","folder","了解归档与模板？",
   new String[]{"archive","归档收起不再使用的计划，历史投入仍然保留。"},
   new String[]{"templates","模板用于重复创建相似安排；选择模板后复制为新计划。"},
   new String[]{"library-actions","复制得到独立的新计划，修改它不会改变原模板。"}),
  topic("reminders","提醒计划","bell","了解提醒计划？",
   new String[]{"add","新增提醒，填写内容，再选择一次、周期或间隔提醒。"},
   new String[]{"mask","提醒到时显示遮罩；达到最低时长后，手动关闭继续工作。"},
   new String[]{"enabled","固定间隔适合每隔 50 分钟喝水；间隔序列适合 5、10、20、30 分钟逐次提醒。"},
   new String[]{"completed","已完成或过期的一次性提醒集中收起，常用提醒留在已启用列表。"}),
  topic("statistics","专注统计","chart","了解专注统计？",
   new String[]{"heatmap","热力图按学习日显示投入；点击一天可查看详情。"},
   new String[]{"day","单日复盘查看任务构成与时间分布，点击日期可跳转。"},
   new String[]{"day-export","导出图片会包含完整图表和任务明细。"},
   new String[]{"trend","趋势分析比较一段时间的变化；任务分析只看长期周期计划。"}),
  topic("insights","专注洞察与导出","grid","了解专注洞察与导出？",
   new String[]{"insights-summary","专注洞察汇总时间去向、星期节奏，并与上一周期比较。"},
   new String[]{"insights-range","选择统计范围；今天尚在累计，也会计入当前周期。"},
   new String[]{"insights-export","图片便于分享；每日明细导出 CSV，便于继续整理和分析。"}),
  topic("computer","电脑使用","monitor","了解电脑使用统计？",
   new String[]{"metric","前台使用统计当前应用；总运行时间还包含后台运行。"},
   new String[]{"minimum","最低时长可隐藏短暂打开的应用。"},
   new String[]{"percent","按排名保留前一部分应用，可同时使用时长筛选。"},
   new String[]{"search","输入名称或文件名，快速查找应用。"},
   new String[]{"watched","关注应用集中展示常用软件，可搜索并切换列表或平铺。"},
   new String[]{"add","添加关注可扫描并多选，也可以手动选择 EXE。"}),
  topic("limits","电脑使用限制","shield","了解电脑使用限制？",
   new String[]{"enable","每日额度限制整台电脑的使用总量，与单个应用的额度分别计算。"},
   new String[]{"budget","设置每天最多使用多久；未开启时不会限制使用。"},
   new String[]{"times","允许时段决定什么时候能使用电脑，支持跨午夜。"},
   new String[]{"save","确认额度与时段后保存；超限会显示遮罩，重启不会解除。"}),
  topic("app-limits","应用限制","monitor","了解分应用限制？",
   new String[]{"manage","在这里添加应用，分别设置完全禁用、每日额度或允许时段。"},
   new String[]{"behavior","受限时先遮挡应用并说明原因；点击返回桌面后才结束该应用。"}),
  topic("strict","一周严格设置","lock","了解一周严格设置？",
   new String[]{"rules","开启后连续 7 天只能收紧：减少额度、缩小时段，或加强休息要求。"},
   new String[]{"state","严格期内不能暂停、删除或放宽有效限制；这里显示到期时间。"},
   new String[]{"activate","先确认当前已保存的设置，再决定是否开启；开启后不能提前结束。"})
 );
 public static List<Topic> topics(){return TOPICS;}
 public static void register(TutorialController controller,BiFunction<String,String,JComponent> target,BiConsumer<String,String> open){
  for(Topic topic:TOPICS)controller.registerTopic(topic.id(),topic.prompt(),steps(topic,target,open));
  // Original feature releases, not this tutorial-only revision.
  update(controller,"app-limits","application-limits","0.1.24-experimental.15","新增分应用限制，可以为不同软件安排使用边界。",target,open);
  update(controller,"strict","local-strict-settings","0.1.24-experimental.15","新增一周严格设置，可了解它如何锁定已保存的要求。",target,open);
  update(controller,"insights","focus-insights-export","0.1.24-experimental.19","新增专注洞察与导出，可回顾时间去向和周期变化。",target,open);
 }
 private static List<TutorialController.Step> steps(Topic topic,BiFunction<String,String,JComponent> target,BiConsumer<String,String> open){
  return topic.copy().stream().map(row->new TutorialController.Step(row[1],()->target.apply(topic.id(),row[0]),()->open.accept(topic.id(),row[0]),null)).toList();
 }
 private static void update(TutorialController controller,String topicId,String featureId,String version,String prompt,BiFunction<String,String,JComponent> target,BiConsumer<String,String> open){
  Topic topic=TOPICS.stream().filter(t->t.id().equals(topicId)).findFirst().orElseThrow();
  controller.registerFeatureTour(new TutorialController.FeatureTour(featureId,version,prompt,steps(topic,target,open)));
 }
}

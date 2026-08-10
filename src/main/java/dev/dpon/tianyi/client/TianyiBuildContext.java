package dev.dpon.tianyi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
/** Client-side helpers for the LLM "build" tool: anchor picking, skill gating and prompt text. */
public final class TianyiBuildContext {
    private TianyiBuildContext() {}

    /** Finds the player's own Tianyi among rendered entities (she is nearby when chatting). */
    public static TianyiEntity findOwnedTianyi(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof TianyiEntity t && t.isOwnedBy(mc.player)) return t;
        }
        return null;
    }

    /** Creative players always can build; others need Tianyi's unlocked skill. */
    public static boolean isBuildAvailable(Minecraft mc) {
        if (mc.player == null) return false;
        if (mc.player.getAbilities().instabuild) return true;
        TianyiEntity t = findOwnedTianyi(mc);
        return t != null && t.hasBuildSkill();
    }

    /** The block the player is looking at becomes the build origin (0,0,0). */
    public static BlockPos findAnchor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        HitResult hit = mc.player.pick(64.0D, partial, false);
        if (hit.getType() == HitResult.Type.BLOCK) return ((BlockHitResult) hit).getBlockPos();
        return null;
    }

    /** OpenAI function schema for the build tool. */
    public static JsonObject buildToolSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject ops = new JsonObject();
        ops.addProperty("type", "array");
        ops.addProperty("description", "建造指令数组，见系统提示中的指令格式");
        props.add("ops", ops);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("ops");
        schema.add("required", required);
        return schema;
    }

    /** System-prompt fragment describing the building skill, gated on availability. */
    public static String systemNote() {
        if (!isBuildAvailable(Minecraft.getInstance())) {
            return "【建造技能】天依目前还没有掌握盖房子的技能（和主人一起睡满 5 晚才能学会，或者主人处于创造模式）。"
                    + "如果主人让你盖房子或建东西，就温柔地拒绝并说自己还不会。";
        }
        return "【建造技能】你掌握了盖房子技能，可以用 build 工具在主人看中的地皮上建房子。"
                + "坐标都是相对坐标：主人看中的那一格记为 (0,0,0)，y=0 是地面，y+1 是一格高。"
                + "ops 是建造指令数组，按 地基→墙→门窗→屋顶→内部陈设→外部装饰 的顺序给出。指令格式：\n"
                + "- box 实心立方体：{op:\"box\", block:\"oak_planks\", x1,y1,z1, x2,y2,z2, hollow:true}\n"
                + "- walls 四面墙（无顶无底）：{op:\"walls\", block:\"oak_planks\", x1,y1,z1, x2,y2,z2}\n"
                + "- set 单格：{op:\"set\", block:\"air\", x,y,z}（block 用 air 可以开门洞窗洞）\n"
                + "- line 直线：{op:\"line\", block:\"oak_fence\", x1,y1,z1, x2,y2,z2}\n"
                + "- door 门：{op:\"door\", block:\"oak_door\", x,y,z, facing:\"north\"}（y 是门下方那格，门占两格高）\n"
                + "- bed 床：{op:\"bed\", block:\"red_bed\", x,y,z, facing:\"north\"}（y 是床脚那格，床头朝向 facing 那一侧，记得让床头朝屋里）\n"
                + "- roof 屋顶：{op:\"roof\", block:\"oak_slab\", x1,z1,x2,z2, y, shape:\"gable\"}（y=墙顶+1，block 要用 *_slab；shape: gable 人字坡/shed 单坡/flat 平顶，可加 overhang:true 出檐）\n"
                + "- scatter 随机撒：{op:\"scatter\", block:\"dandelion\", x,y,z, radius:3, count:8}\n"
                + "block 支持材质混搭：{block:\"oak_planks*8, mossy_cobblestone*2, cobblestone\"}（*后面是权重，按位置稳定取样）。"
                + "门和床会自动补全另一半；火把/花草等需要支撑的方块，放不上去会自己跳过，所以把它们放在实心的地板或墙面上。"
                + "造房要宜居：地基略高于地面、门开 2 格宽不挡路、室内留 2 格高走道、屋顶盖严不漏雨、室内摆床/箱/灯/桌等家具。"
                + "材料由主人准备，你不需要管。";
    }
}

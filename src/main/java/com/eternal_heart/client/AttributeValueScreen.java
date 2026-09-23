package com.eternal_heart.client;

import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CustomAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 自定义属性的数值 / 操作编辑弹层（由 {@link AttributeListScreen} 打开）。
 *
 * <p>数值语义为修饰符原始 amount（不做总倍率换算），操作类型在
 * addition / multiply_base / multiply_total 之间循环切换。</p>
 */
public class AttributeValueScreen extends Screen {

    private static final int COL_OVERLAY = 0xB0040609;
    private static final int COL_PANEL = 0xF60E1521;
    private static final int COL_BORDER = 0xFF9A7B3F;
    private static final int COL_GOLD = 0xFFE8C860;
    private static final int COL_TEXT = 0xFFE6EAF0;
    private static final int COL_TEXT_DIM = 0xFF8A93A0;

    private final AttributeListScreen parent;
    private final ResourceLocation attribute;
    private final String initialValue;
    private AttributeModifier.Operation operation;
    private EditBox valueBox;
    private Button operationBtn;
    private Button confirmBtn;
    private Button removeBtn;
    private Button cancelBtn;
    private int panelX, panelY, panelW, panelH;

    public AttributeValueScreen(AttributeListScreen parent, ResourceLocation attribute) {
        super(Component.translatable("config.eternal_heart.attribute_value.title"));
        this.parent = parent;
        this.attribute = attribute;
        CustomAttributes.Spec spec = parent.spec(attribute);
        this.operation = spec == null ? AttributeModifier.Operation.ADDITION : spec.operation();
        this.initialValue = Numbers.format(spec == null ? 0.0 : spec.value());
    }

    @Override
    protected void init() {
        panelW = Math.max(230, Math.min(this.width - 12, 300));
        panelH = 134;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        valueBox = new EditBox(this.font, panelX + 12, panelY + 60, panelW - 24, 18,
                Component.translatable("config.eternal_heart.attribute_value.value"));
        valueBox.setMaxLength(48);
        // 与配置面板一致：允许符号与科学计数法的中间态
        valueBox.setFilter(text -> text.isEmpty()
                || text.matches("[-+]?(\\d+\\.?\\d*|\\.\\d*)?([eE][-+]?\\d*)?"));
        valueBox.setValue(initialValue);
        this.addRenderableWidget(valueBox);

        operationBtn = this.addRenderableWidget(Button.builder(operationText(), b -> {
            int next = (CustomAttributes.OPERATIONS.indexOf(operation) + 1) % CustomAttributes.OPERATIONS.size();
            operation = CustomAttributes.OPERATIONS.get(next);
            b.setMessage(operationText());
        }).bounds(panelX + 12, panelY + 82, panelW - 24, 18).build());

        int btnY = panelY + panelH - 24;
        confirmBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.attribute_value.confirm"), b -> confirm())
                .bounds(panelX + panelW - 8 - 58, btnY, 58, 18).build());
        removeBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.attribute_value.remove"), b -> remove())
                .bounds(panelX + panelW - 8 - 58 * 2 - 4, btnY, 58, 18).build());
        cancelBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("config.eternal_heart.attribute_value.cancel"), b -> onClose())
                .bounds(panelX + panelW - 8 - 58 * 3 - 8, btnY, 58, 18).build());
    }

    private Component operationText() {
        return Component.translatable("config.eternal_heart.attribute_value.operation",
                CustomAttributes.operationName(operation));
    }

    private void confirm() {
        try {
            double value = Double.parseDouble(valueBox.getValue().trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            parent.put(attribute, value, operation);
            Minecraft.getInstance().setScreen(parent);
        } catch (NumberFormatException e) {
            valueBox.setTextColor(0xFFFF7777);
        }
    }

    private void remove() {
        parent.remove(attribute);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, COL_BORDER);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COL_BORDER);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, COL_BORDER);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COL_BORDER);

        g.drawCenteredString(this.font, tr("title"), panelX + panelW / 2, panelY + 8, COL_GOLD);
        g.drawString(this.font, trim(Component.translatable(CustomAttributes.displayNameKey(attribute)).getString(),
                panelW - 24), panelX + 12, panelY + 26, COL_TEXT);
        g.drawString(this.font, trim(attribute.toString(), panelW - 24),
                panelX + 12, panelY + 38, COL_TEXT_DIM);
        g.drawString(this.font, tr("value"), panelX + 12, panelY + 50, COL_TEXT_DIM);

        valueBox.render(g, mx, my, pt);
        operationBtn.render(g, mx, my, pt);
        confirmBtn.render(g, mx, my, pt);
        removeBtn.render(g, mx, my, pt);
        cancelBtn.render(g, mx, my, pt);
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.attribute_value." + key, args).getString();
    }

    private String trim(String s, int w) {
        if (w <= 0) return "";
        if (this.font.width(s) <= w) return s;
        return this.font.plainSubstrByWidth(s, Math.max(0, w - 6)) + "…";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

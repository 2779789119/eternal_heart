package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiEditBox;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CustomAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
public class AttributeValueScreen extends UiScreen {

    private static final int FOOTER_H = 38;

    private final AttributeListScreen parent;
    private final ResourceLocation attribute;
    private final String initialValue;
    private AttributeModifier.Operation operation;
    private UiEditBox valueBox;
    private UiButton operationBtn;

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
        layoutPanel(380, 200, 280, 196);

        valueBox = new UiEditBox(this.font, panelX + 16, panelY + 84, panelW - 32, 22,
                Component.translatable("config.eternal_heart.attribute_value.value"));
        valueBox.setMaxLength(48);
        // 与配置面板一致：允许符号与科学计数法的中间态
        valueBox.setFilter(text -> text.isEmpty()
                || text.matches("[-+]?(\\d+\\.?\\d*|\\.\\d*)?([eE][-+]?\\d*)?"));
        valueBox.setValue(initialValue);
        this.addRenderableWidget(valueBox);

        operationBtn = this.addRenderableWidget(UiButton.secondary(
                panelX + 16, panelY + 114, panelW - 32, 22, operationText(), () -> {
                    int next = (CustomAttributes.OPERATIONS.indexOf(operation) + 1)
                            % CustomAttributes.OPERATIONS.size();
                    operation = CustomAttributes.OPERATIONS.get(next);
                    operationBtn.setMessage(operationText());
                }));

        int btnY = panelY + panelH - FOOTER_H + 8;
        this.addRenderableWidget(UiButton.primary(panelX + panelW - 16 - 64, btnY, 64, 22,
                Component.translatable("config.eternal_heart.attribute_value.confirm"), this::confirm));
        this.addRenderableWidget(UiButton.danger(panelX + panelW - 16 - 64 - 8 - 64, btnY, 64, 22,
                Component.translatable("config.eternal_heart.attribute_value.remove"), this::remove));
        this.addRenderableWidget(UiButton.ghost(panelX + panelW - 16 - 64 - 8 - 64 - 8 - 64, btnY, 64, 22,
                Component.translatable("config.eternal_heart.attribute_value.cancel"), this::onClose));
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
            valueBox.setTextColor(UiTheme.DANGER);
        }
    }

    private void remove() {
        parent.remove(attribute);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        drawPanel(g);

        // 顶部强调线
        UiTheme.hGradient(g, panelX + 18, panelX + panelW / 2, panelY + 1, 0x005EEAD4, 0xA05EEAD4);
        UiTheme.hGradient(g, panelX + panelW / 2, panelX + panelW - 18, panelY + 1, 0xA05EEAD4, 0x005EEAD4);
        g.drawString(this.font, Component.literal(tr("title")).withStyle(ChatFormatting.BOLD),
                panelX + 16, panelY + 12, UiTheme.TEXT, false);

        String name = Component.translatable(CustomAttributes.displayNameKey(attribute)).getString();
        g.drawString(this.font, trim(name, panelW - 32), panelX + 16, panelY + 38, UiTheme.TEXT);
        g.drawString(this.font, trim(attribute.toString(), panelW - 32), panelX + 16, panelY + 52,
                UiTheme.TEXT_MUTED);
        g.drawString(this.font, tr("value"), panelX + 16, panelY + 70, UiTheme.TEXT_DIM);

        drawFooterDivider(g, FOOTER_H);
        super.render(g, mx, my, pt);
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.attribute_value." + key, args).getString();
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

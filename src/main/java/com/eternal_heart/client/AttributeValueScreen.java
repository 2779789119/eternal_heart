package com.eternal_heart.client;

import com.eternal_heart.client.ui.UiButton;
import com.eternal_heart.client.ui.UiScreen;
import com.eternal_heart.client.ui.UiTextField;
import com.eternal_heart.client.ui.UiTheme;
import com.eternal_heart.core.Numbers;
import com.eternal_heart.features.CustomAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * 自定义属性的数值 / 操作编辑弹层（由 {@link AttributeListScreen} 打开）。
 *
 * <p>数值语义为修饰符原始 amount（不做总倍率换算）；操作类型用三个分段按钮直接点选
 * （addition / multiply_base / multiply_total），比「点一下循环切换」更直观。</p>
 */
public class AttributeValueScreen extends UiScreen {

    private static final int HEADER = 46;
    private static final int FOOTER = 38;

    private final AttributeListScreen parent;
    private final ResourceLocation attribute;
    private final String initialValue;

    private AttributeModifier.Operation operation;
    private UiTextField valueField;
    private final UiButton[] opButtons = new UiButton[3];
    private String status = "";
    private int statusColor = UiTheme.TEXT_MUTED;

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
        clearWidgets();
        headerH = HEADER;
        footerH = FOOTER;
        layoutPanel(400, 226, 320, 220);

        int left = contentLeft();
        int fieldW = panelW - 32;

        valueField = add(new UiTextField(left, panelY + 104, fieldW, 22)
                .setHint(Component.translatable("config.eternal_heart.attribute_value.value"))
                .setMaxLength(48)
                .setText(initialValue)
                // 与配置面板一致：允许符号与科学计数法的中间态
                .setFilter(text -> text.isEmpty()
                        || text.matches("[-+]?(\\d+\\.?\\d*|\\.\\d*)?([eE][-+]?\\d*)?"))
                .selectAllOnFocus()
                .setOnSubmit(this::confirm));
        focusWidget(valueField);

        List<AttributeModifier.Operation> ops = CustomAttributes.OPERATIONS;
        int gap = 6;
        int btnW = (fieldW - gap * (ops.size() - 1)) / ops.size();
        for (int i = 0; i < ops.size() && i < opButtons.length; i++) {
            AttributeModifier.Operation candidate = ops.get(i);
            opButtons[i] = add(UiButton.segment(left + i * (btnW + gap), panelY + 152, btnW, 22,
                    Component.literal(CustomAttributes.operationName(candidate)), () -> {
                        operation = candidate;
                        refreshOperations();
                        status = "";
                    }));
        }
        refreshOperations();

        int btnY = panelY + panelH - FOOTER + 8;
        add(UiButton.primary(panelX + panelW - 16 - 66, btnY, 66, 22,
                Component.translatable("config.eternal_heart.attribute_value.confirm"), this::confirm));
        add(UiButton.danger(panelX + panelW - 16 - 66 - 8 - 66, btnY, 66, 22,
                Component.translatable("config.eternal_heart.attribute_value.remove"), this::remove));
        add(UiButton.ghost(panelX + panelW - 16 - 66 - 8 - 66 - 8 - 66, btnY, 66, 22,
                Component.translatable("config.eternal_heart.attribute_value.cancel"), this::onClose));
    }

    private void refreshOperations() {
        List<AttributeModifier.Operation> ops = CustomAttributes.OPERATIONS;
        for (int i = 0; i < opButtons.length; i++) {
            if (opButtons[i] != null && i < ops.size()) {
                opButtons[i].setSelected(ops.get(i) == operation);
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawHeader(g, this.title, null, HEADER);

        String name = Component.translatable(CustomAttributes.displayNameKey(attribute)).getString();
        g.drawString(this.font, Component.literal(name).withStyle(ChatFormatting.BOLD)
                        .getString(), contentLeft(), panelY + 56, UiTheme.TEXT, false);
        g.drawString(this.font, trim(attribute.toString(), panelW - 32),
                contentLeft(), panelY + 70, UiTheme.TEXT_MUTED, false);

        g.drawString(this.font, Component.translatable("config.eternal_heart.attribute_value.value").getString(),
                contentLeft(), panelY + 92, UiTheme.TEXT_DIM, false);
        g.drawString(this.font, tr("operation_label"), contentLeft(), panelY + 140, UiTheme.TEXT_DIM, false);

        renderWidgets(g, mouseX, mouseY, partial);

        drawFooterDivider(g, FOOTER);
        drawStatus(g, status, statusColor, panelW - 250);
    }

    private void confirm() {
        try {
            double value = Double.parseDouble(valueField.getValue().trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            parent.put(attribute, value, operation);
            Minecraft.getInstance().setScreen(parent);
        } catch (NumberFormatException e) {
            valueField.setTextColor(UiTheme.DANGER);
            status = tr("invalid");
            statusColor = UiTheme.DANGER;
        }
    }

    private void remove() {
        parent.remove(attribute);
        Minecraft.getInstance().setScreen(parent);
    }

    private String tr(String key, Object... args) {
        return Component.translatable("config.eternal_heart.attribute_value." + key, args).getString();
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        // 焦点不在输入框时（例如停在操作类型按钮上）回车同样提交
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

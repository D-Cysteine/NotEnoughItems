package codechicken.nei.guihook;

import codechicken.lib.gui.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Point;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import static codechicken.lib.gui.GuiDraw.drawMultilineTip;
import static codechicken.lib.gui.GuiDraw.fontRenderer;
import static codechicken.lib.gui.GuiDraw.getMousePosition;
import static codechicken.lib.gui.GuiDraw.renderEngine;

public class GuiContainerManager
{
    public GuiContainer window;

    public static RenderItem drawItems = new RenderItem();
    public static LinkedList<IContainerTooltipHandler> tooltipHandlers = new LinkedList<>();
    public static LinkedList<IContainerInputHandler> inputHandlers = new LinkedList<>();
    public static LinkedList<IContainerDrawHandler> drawHandlers = new LinkedList<>();
    public static LinkedList<IContainerObjectHandler> objectHandlers = new LinkedList<>();
    public static LinkedList<IContainerSlotClickHandler> slotClickHandlers = new LinkedList<>();

    static {
        addSlotClickHandler(new DefaultSlotClickHandler());
    }

    public static GuiContainerManager getManager() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        return gui instanceof GuiContainer ? getManager((GuiContainer) gui) : null;
    }

    public static GuiContainerManager getManager(GuiContainer gui) {
        //gets GuiContainer.manager using ASM
        return null;
    }

    /**
     * Register a new Tooltip render handler;
     *
     * @param handler The handler to register
     */
    public static void addTooltipHandler(IContainerTooltipHandler handler) {
        tooltipHandlers.add(handler);
    }

    /**
     * Register a new Input handler;
     *
     * @param handler The handler to register
     */
    public static void addInputHandler(IContainerInputHandler handler) {
        inputHandlers.add(handler);
    }

    /**
     * Register a new Drawing handler;
     *
     * @param handler The handler to register
     */
    public static void addDrawHandler(IContainerDrawHandler handler) {
        drawHandlers.add(handler);
    }

    /**
     * Register a new Object handler;
     *
     * @param handler The handler to register
     */
    public static void addObjectHandler(IContainerObjectHandler handler) {
        objectHandlers.add(handler);
    }

    /**
     * Care needs to be taken with this method. It will insert your handler at the start of the list to be called first. You may need to simply edit the list yourself.
     *
     * @param handler The handler to register.
     */
    public static void addSlotClickHandler(IContainerSlotClickHandler handler) {
        slotClickHandlers.addFirst(handler);
    }

    public static FontRenderer getFontRenderer(ItemStack stack) {
        if (stack != null && stack.getItem() != null) {
            FontRenderer f = stack.getItem().getFontRenderer(stack);
            if (f != null)
                return f;
        }
        return fontRenderer;
    }

    /**
     * Extra lines are often used for more information. For example enchantments, potion effects and mob spawner contents.
     *
     * @param stack       The item to get the name for.
     * @param gui             An instance of the currentscreen passed to tooltip handlers. If null, only gui inspecific handlers should respond
     * @param includeHandlers If true tooltip handlers will add to the item tip
     * @return A list of Strings representing the text to be displayed on each line of the tool tip.
     */
    public static List<String> itemDisplayNameMultiline(ItemStack stack, GuiContainer gui, boolean includeHandlers) {
        List<String> namelist = null;
        try {
            namelist = stack.getTooltip(Minecraft.getMinecraft().thePlayer, includeHandlers && Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
        } catch (Throwable ignored) {}

        if (namelist == null)
            namelist = new ArrayList<>();

        if (namelist.size() == 0)
            namelist.add("Unnamed");

        if (namelist.get(0) == null || namelist.get(0).equals(""))
            namelist.set(0, "Unnamed");

        if (includeHandlers) {
            for (IContainerTooltipHandler handler : tooltipHandlers) {
                namelist = handler.handleItemDisplayName(gui, stack, namelist);
            }
        }

        namelist.set(0, stack.getRarity().rarityColor.toString() + namelist.get(0));
        for (int i = 1; i < namelist.size(); i++)
            namelist.set(i, "\u00a77" + namelist.get(i));

        return namelist;
    }

    /**
     * The general name of this item.
     *
     * @param itemstack The {@link ItemStack} to get the name for.
     * @return The first line of the multiline display name.
     */
    public static String itemDisplayNameShort(ItemStack itemstack) {
        List<String> list = itemDisplayNameMultiline(itemstack, null, false);
        return list.get(0);
    }

    /**
     * Concatenates the multiline display name into one line for easy searching using string and {@link Pattern} functions.
     *
     * @param itemstack The stack to get the name for
     * @return The multiline display name of this item separated by '#'
     */
    public static String concatenatedDisplayName(ItemStack itemstack, boolean includeHandlers) {
        List<String> list = itemDisplayNameMultiline(itemstack, null, includeHandlers);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String name : list) {
            if (first) {
                first = false;
            } else {
                sb.append("#");
            }
            sb.append(name);
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(sb.toString());
    }

    public static void drawItem(int i, int j, ItemStack itemstack) {
        drawItem(i, j, itemstack, getFontRenderer(itemstack));
    }

    private static int modelviewDepth = -1;
    private static final HashSet<String> stackTraces = new HashSet<>();

    public static void drawItem(int i, int j, ItemStack itemstack, FontRenderer fontRenderer) {
        enable3DRender();
        float zLevel = drawItems.zLevel += 100F;
        try {
            drawItems.renderItemAndEffectIntoGUI(fontRenderer, renderEngine, itemstack, i, j);
            drawItems.renderItemOverlayIntoGUI(fontRenderer, renderEngine, itemstack, i, j);

            if (!checkMatrixStack())
                throw new IllegalStateException("Modelview matrix stack too deep");
            if (Tessellator.instance.isDrawing)
                throw new IllegalStateException("Still drawing");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stackTrace = itemstack + sw.toString();
            if (!stackTraces.contains(stackTrace)) {
                System.err.println("Error while rendering: " + itemstack);
                e.printStackTrace();
                stackTraces.add(stackTrace);
            }

            restoreMatrixStack();
            if (Tessellator.instance.isDrawing)
                Tessellator.instance.draw();

            drawItems.zLevel = zLevel;
            drawItems.renderItemIntoGUI(fontRenderer, renderEngine, new ItemStack(Blocks.fire), i, j);
        }

        enable2DRender();
        drawItems.zLevel = zLevel - 100;
    }

    public static void enableMatrixStackLogging() {
        modelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
    }

    public static void disableMatrixStackLogging() {
        modelviewDepth = -1;
    }

    public static boolean checkMatrixStack() {
        return modelviewDepth < 0 || GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) == modelviewDepth;
    }

    public static void restoreMatrixStack() {
        if (modelviewDepth >= 0)
            for (int i = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH); i > modelviewDepth; i--)
                GL11.glPopMatrix();
    }

    public static void setColouredItemRender(boolean enable) {
        drawItems.renderWithColor = !enable;
    }

    public static void enable3DRender() {
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void enable2DRender() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    private int clickHandled = 0;
    private final List<IContainerTooltipHandler> instanceTooltipHandlers;

    public GuiContainerManager(GuiContainer screen) {
        window = screen;
        if (screen instanceof IContainerTooltipHandler) {
            instanceTooltipHandlers = Collections.synchronizedList(new LinkedList<>());
            instanceTooltipHandlers.add((IContainerTooltipHandler) screen);
            instanceTooltipHandlers.addAll(tooltipHandlers);
        } else
            instanceTooltipHandlers = tooltipHandlers;
    }

    public static ItemStack getStackMouseOver(GuiContainer window) {
        Point mousePos = getMousePosition();

        for (IContainerObjectHandler objectHandler : objectHandlers) {
            ItemStack item = objectHandler.getStackUnderMouse(window, mousePos.x, mousePos.y);
            if (item != null)
                return item;
        }

        Slot slot = getSlotMouseOver(window);
        if (slot != null)
            return slot.getStack();

        return null;
    }

    public static Slot getSlotMouseOver(GuiContainer window) {
        Point mousePos = getMousePosition();
        if (getManager(window).objectUnderMouse(mousePos.x, mousePos.y))
            return null;

        return window.getSlotAtPosition(mousePos.x, mousePos.y);
    }

    public void load() {
        clickHandled = 0;
        for (IContainerObjectHandler objectHandler : objectHandlers)
            objectHandler.load(window);
    }

    /**
     * Called from updateScreen
     */
    public void updateScreen() {
        for (IContainerObjectHandler objectHandler : objectHandlers)
            objectHandler.guiTick(window);
    }

    /**
     * Override for keyTyped
     */
    public boolean lastKeyTyped(int keyID, char keyChar) {
        if (keyID == 1)
            return false;

        for (IContainerInputHandler inputhander : inputHandlers)
            if (inputhander.lastKeyTyped(window, keyChar, keyID))
                return true;

        return false;
    }

    public boolean firstKeyTyped(char keyChar, int keyID) {
        for (IContainerInputHandler inputhander : inputHandlers)
            inputhander.onKeyTyped(window, keyChar, keyID);

        for (IContainerInputHandler inputhander : inputHandlers)
            if (inputhander.keyTyped(window, keyChar, keyID))
                return true;

        return false;
    }

    public boolean mouseClicked(int mousex, int mousey, int button) {
        for (IContainerInputHandler inputhander : inputHandlers)
            inputhander.onMouseClicked(window, mousex, mousey, button);

        for (IContainerInputHandler inputhander : inputHandlers)
            if (inputhander.mouseClicked(window, mousex, mousey, button)) {
                clickHandled |= 1 << button;
                return true;
            }

        return false;
    }

    public void mouseScrolled(int scrolled) {
        Point mousepos = getMousePosition();

        for (IContainerInputHandler inputHandler : inputHandlers)
            inputHandler.onMouseScrolled(window, mousepos.x, mousepos.y, scrolled);

        for (IContainerInputHandler inputHandler : inputHandlers)
            if (inputHandler.mouseScrolled(window, mousepos.x, mousepos.y, scrolled))
                return;

        if (window instanceof IGuiHandleMouseWheel)
            ((IGuiHandleMouseWheel) window).mouseScrolled(scrolled);
    }

    /**
     * Override for mouseMovedOrUp
     */
    public boolean overrideMouseUp(int mousex, int mousey, int button) {
        if (button >= 0 && (clickHandled & 1 << button) != 0) {
            clickHandled &= ~(1 << button);
            mouseUp(mousex, mousey, button);
            return true;
        }
        return false;
    }

    public void mouseUp(int mousex, int mousey, int button) {
        for (IContainerInputHandler inputhander : inputHandlers)
            inputhander.onMouseUp(window, mousex, mousey, button);
    }

    /**
     * Called from mouseClickMove
     */
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {
        for (IContainerInputHandler inputhander : inputHandlers)
            inputhander.onMouseDragged(window, mousex, mousey, button, heldTime);
    }

    /**
     * Called at the start of drawScreen
     */
    public void preDraw() {
        for (IContainerDrawHandler drawHandler : drawHandlers)
            drawHandler.onPreDraw(window);
    }

    public void renderObjects(int mousex, int mousey) {
        GL11.glTranslatef(-window.guiLeft, -window.guiTop, 200F);
        for (IContainerDrawHandler drawHandler : drawHandlers)
            drawHandler.renderObjects(window, mousex, mousey);

        for (IContainerDrawHandler drawHandler : drawHandlers)
            drawHandler.postRenderObjects(window, mousex, mousey);
        GL11.glTranslatef(window.guiLeft, window.guiTop, -200F);
    }

    public void renderToolTips(int mousex, int mousey) {
        List<String> tooltip = new LinkedList<>();
        FontRenderer font = GuiDraw.fontRenderer;

        synchronized (instanceTooltipHandlers) {
            for (IContainerTooltipHandler handler : instanceTooltipHandlers)
                tooltip = handler.handleTooltip(window, mousex, mousey, tooltip);
        }
        
        if (tooltip.isEmpty() && shouldShowTooltip(window)) {//mouseover tip, not holding an item
            ItemStack stack = getStackMouseOver(window);
            font = getFontRenderer(stack);
            if (stack != null)
                tooltip = itemDisplayNameMultiline(stack, window, true);

            synchronized (instanceTooltipHandlers) {
                for (IContainerTooltipHandler handler : instanceTooltipHandlers)
                    tooltip = handler.handleItemTooltip(window, stack, mousex, mousey, tooltip);
            }
        }

        if (tooltip.size() > 0)
            tooltip.set(0, tooltip.get(0) + GuiDraw.TOOLTIP_LINESPACE);//add space after 'title'

        drawMultilineTip(font, mousex + 12, mousey - 12, tooltip);
    }

    public static boolean shouldShowTooltip(GuiContainer window) {
        for (IContainerObjectHandler handler : objectHandlers)
            if (!handler.shouldShowTooltip(window))
                return false;

        return window.mc.thePlayer.inventory.getItemStack() == null;
    }

    public void renderSlotUnderlay(Slot slot) {
        for (IContainerDrawHandler drawHandler : drawHandlers)
            drawHandler.renderSlotUnderlay(window, slot);
    }

    public void renderSlotOverlay(Slot slot) {
        for (IContainerDrawHandler drawHandler : drawHandlers)
            drawHandler.renderSlotOverlay(window, slot);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    /**
     * Returns true if there is an object of yours obscuring the slot that the mouse would otherwise be hovering over.
     */
    public boolean objectUnderMouse(int mousex, int mousey) {
        for (IContainerObjectHandler objectHandler : objectHandlers)
            if (objectHandler.objectUnderMouse(window, mousex, mousey))
                return true;

        return false;
    }

    public void handleMouseClick(Slot slot, int slotIndex, int button, int modifier) {
        for (IContainerSlotClickHandler handler : slotClickHandlers)
            handler.beforeSlotClick(window, slotIndex, button, slot, modifier);

        boolean eventHandled = false;
        for (IContainerSlotClickHandler handler : slotClickHandlers)
            eventHandled = handler.handleSlotClick(window, slotIndex, button, slot, modifier, eventHandled);

        for (IContainerSlotClickHandler handler : slotClickHandlers)
            handler.afterSlotClick(window, slotIndex, button, slot, modifier);
    }

    // Support inputting Chinese characters
    public void handleKeyboardInput() {
        // Support for LWGJL 2.9.0 or later
        int k = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        if (Keyboard.getEventKeyState() || (k == 0 && Character.isDefined(c))) {
            try {
                keyTyped(c, k);
            } catch (java.lang.IndexOutOfBoundsException e) {
                System.err.println("Caught out of bounds exception pressing " + c + " " + k);
                e.printStackTrace();
            }
        }

        window.mc.func_152348_aa();
    }


    public void keyTyped(char c, int k) {
        if (firstKeyTyped(c, k))
            return;

        callKeyTyped(window, c, k);
    }

    private static void callKeyTyped(GuiContainer window, char c, int k) {
        //calls GuiContainer.keyTyped using ASM generated forwarder
    }

    /**
     * Delegate for changing item rendering for certain slots. Eg. Shrinking text for large itemstacks
     */
    public void drawSlotItem(Slot slot, ItemStack stack, int x, int y, String quantity) {
        if(window instanceof IGuiSlotDraw)
            ((IGuiSlotDraw)window).drawSlotItem(slot, stack, x, y, quantity);
        else {
            drawItems.renderItemAndEffectIntoGUI(fontRenderer, window.mc.getTextureManager(), stack, x, y);
            drawItems.renderItemOverlayIntoGUI(fontRenderer, window.mc.getTextureManager(), stack, x, y, quantity);
        }
    }

    /**
     * Implementation for handleMouseClick
     */
    public void handleSlotClick(int slotIndex, int button, int modifiers) {
        if (slotIndex == -1)
            return;

        if (window instanceof IGuiClientSide)//send the calls directly to the container bypassing the MPController window send
            window.mc.thePlayer.openContainer.slotClick(slotIndex, button, modifiers, window.mc.thePlayer);
        else
            window.mc.playerController.windowClick(window.inventorySlots.windowId, slotIndex, button, modifiers, window.mc.thePlayer);
    }

    /**
     * Called from handleMouseInput
     */
    public void handleMouseWheel() {
        int i = Mouse.getEventDWheel();
        if (i != 0)
            mouseScrolled(i > 0 ? 1 : -1);
    }
}

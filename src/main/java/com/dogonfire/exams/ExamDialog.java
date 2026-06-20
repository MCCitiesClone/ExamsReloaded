package com.dogonfire.exams;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Renders a single exam question as a native Minecraft dialog (Paper Dialog API,
 * 1.21.7+). Each answer option is a multi-action button whose server-side
 * {@code customClick} callback submits the chosen answer — no chat or inventory.
 */
public final class ExamDialog
{
    // Exams.yml lets question/option text use '&' colour codes, so deserialize them.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final int BUTTON_WIDTH = 220;

    private ExamDialog()
    {
    }

    public static void show(Player player, String examName, String question, List<String> options,
            int questionNumber, int totalQuestions)
    {
        Component title = Component.text(examName + " Exam — " + questionNumber + "/" + totalQuestions,
                NamedTextColor.GOLD);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(LEGACY.deserialize(question == null ? "" : question)));

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++)
        {
            // Effectively-final locals for capture inside the callback.
            final String letter = String.valueOf((char) ('A' + i));
            final String optionText = options.get(i) == null ? "" : options.get(i);

            Component label = Component.text(letter + ". ", NamedTextColor.YELLOW)
                    .append(LEGACY.deserialize(optionText).colorIfAbsent(NamedTextColor.WHITE));

            DialogAction action = DialogAction.customClick(
                    (view, audience) -> Bukkit.getScheduler().runTask(Exams.instance(),
                            () -> ExamManager.submitAnswer(player, letter)),
                    ClickCallback.Options.builder().uses(1).build());

            buttons.add(ActionButton.create(label, Component.text("Choose " + letter), BUTTON_WIDTH, action));
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));

        player.showDialog(dialog);
    }
}

package com.juanmuscaria.modpackdirector.ui;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ExternalUiHelperMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExternalUiHelperMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ExternalUiHelperMain <request.json> <response.json>");
            System.exit(2);
            return;
        }

        Path requestPath = Paths.get(args[0]);
        Path responsePath = Paths.get(args[1]);
        ExternalUiProtocol.Request request =
            MAPPER.readValue(requestPath.toFile(), ExternalUiProtocol.Request.class);

        AtomicReference<ExternalUiProtocol.Response> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(show(request)));
        MAPPER.writeValue(responsePath.toFile(), result.get());
        System.exit(0);
    }

    private static ExternalUiProtocol.Response show(ExternalUiProtocol.Request request) {
        switch (request.type) {
            case "selection":
                return showSelection(request);
            case "consent":
                return showConsent(request);
            case "message":
                return showMessage(request);
            case "manual-download":
                return showManualDownload(request);
            case "error":
                return showErrors(request);
            default:
                throw new IllegalArgumentException("Unknown external UI request type: " + request.type);
        }
    }

    private static ExternalUiProtocol.Response showSelection(ExternalUiProtocol.Request request) {
        ExternalUiProtocol.Response response = new ExternalUiProtocol.Response();
        response.accepted = false;

        JDialog dialog = createDialog(request.packName);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        if (request.title != null && !request.title.isEmpty()) {
            JLabel title = new JLabel(request.title);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(title);
        }

        Map<String, AbstractButton> controls = new LinkedHashMap<>();
        for (ExternalUiProtocol.Option option : request.options) {
            JCheckBox box = new JCheckBox(option.name, option.selected);
            controls.put(option.id, box);
            content.add(box);
            addDescription(content, option.description);
        }

        for (ExternalUiProtocol.Group group : request.groups) {
            JPanel groupPanel = new JPanel();
            groupPanel.setLayout(new BoxLayout(groupPanel, BoxLayout.Y_AXIS));
            groupPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), group.name,
                TitledBorder.CENTER, TitledBorder.TOP
            ));
            ButtonGroup buttonGroup = new ButtonGroup();
            for (ExternalUiProtocol.Option option : group.options) {
                JRadioButton button = new JRadioButton(option.name, option.selected);
                controls.put(option.id, button);
                buttonGroup.add(button);
                groupPanel.add(button);
                addDescription(groupPanel, option.description);
            }
            content.add(groupPanel);
        }

        JButton next = new JButton(nonEmpty(request.buttonLabel, ""));
        next.addActionListener(e -> {
            controls.forEach((id, control) -> response.selections.put(id, control.isSelected()));
            response.accepted = true;
            response.cancelled = false;
            dialog.dispose();
        });

        installCloseHandling(dialog, response);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(next);

        dialog.add(new JScrollPane(content), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        showDialog(dialog);
        return response;
    }

    private static ExternalUiProtocol.Response showConsent(ExternalUiProtocol.Request request) {
        ExternalUiProtocol.Response response = new ExternalUiProtocol.Response();

        JTextArea mods = new JTextArea();
        mods.setEditable(false);
        mods.setLineWrap(true);
        mods.setWrapStyleWord(true);

        StringBuilder body = new StringBuilder();
        if (request.message != null) {
            body.append(request.message).append("\n\n");
        }
        for (ExternalUiProtocol.ModEntry mod : request.mods) {
            body.append(mod.name == null ? "" : mod.name).append('\n');
            if (mod.source != null) {
                body.append("  ").append(mod.source).append('\n');
            }
            if (mod.url != null) {
                body.append("  ").append(mod.url).append('\n');
            }
            if (mod.target != null) {
                body.append("  -> ").append(mod.target).append('\n');
            }
            body.append('\n');
        }
        mods.setText(body.toString());
        mods.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(mods);
        scroll.setPreferredSize(new Dimension(700, 350));

        Object[] options = {
            nonEmpty(request.acceptLabel, ""),
            nonEmpty(request.cancelLabel, "")
        };
        int choice = JOptionPane.showOptionDialog(
            null,
            scroll,
            nonEmpty(request.title, request.packName),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        response.accepted = choice == 0;
        response.cancelled = choice != 0;
        return response;
    }

    private static ExternalUiProtocol.Response showMessage(ExternalUiProtocol.Request request) {
        ExternalUiProtocol.Response response = new ExternalUiProtocol.Response();
        Object[] options = {nonEmpty(request.buttonLabel, "")};
        int choice = JOptionPane.showOptionDialog(
            null,
            request.message,
            nonEmpty(request.title, request.packName),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[0]
        );
        response.accepted = choice == 0;
        response.cancelled = choice != 0;
        return response;
    }

    private static ExternalUiProtocol.Response showManualDownload(ExternalUiProtocol.Request request) {
        ExternalUiProtocol.Response response = new ExternalUiProtocol.Response();
        Path selectedFile = ManualDownloadDialog.show(
            null,
            request.url,
            request.expectedFileName,
            request.target,
            key -> localized(request, key)
        );

        if (selectedFile != null) {
            response.accepted = true;
            response.cancelled = false;
            response.selectedFile = selectedFile.toAbsolutePath().normalize().toString();
        } else {
            response.accepted = false;
            response.cancelled = true;
        }
        return response;
    }

    private static ExternalUiProtocol.Response showErrors(ExternalUiProtocol.Request request) {
        ExternalUiProtocol.Response response = new ExternalUiProtocol.Response();

        StringBuilder text = new StringBuilder();
        if (request.message != null && !request.message.isEmpty()) {
            text.append(request.message).append("\n\n");
        }
        for (ExternalUiProtocol.ErrorEntry error : request.errors) {
            text.append('[').append(error.level).append("] ")
                .append(error.message == null ? "" : error.message).append('\n');
            if (error.cause != null && !error.cause.isEmpty()) {
                text.append("    ")
                    .append(localized(request, "modpack_director.error.cause"))
                    .append(' ')
                    .append(error.cause)
                    .append('\n');
            }
            text.append('\n');
        }

        JTextArea area = new JTextArea(text.toString(), 12, 60);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);

        Object[] options = {nonEmpty(request.buttonLabel, "")};
        JOptionPane.showOptionDialog(
            null,
            new JScrollPane(area),
            nonEmpty(request.title, request.packName),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[0]
        );
        return response;
    }

    private static JDialog createDialog(String title) {
        JDialog dialog = new JDialog((Frame) null, nonEmpty(title, "Contents Director"), true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        return dialog;
    }

    private static void installCloseHandling(
        JDialog dialog,
        ExternalUiProtocol.Response response
    ) {
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                response.accepted = false;
                response.cancelled = true;
                dialog.dispose();
            }
        });
    }

    private static void showDialog(JDialog dialog) {
        dialog.setMinimumSize(new Dimension(600, 400));
        dialog.pack();
        dialog.setSize(
            Math.max(600, dialog.getWidth()),
            Math.max(400, dialog.getHeight())
        );
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static void addDescription(JPanel panel, String description) {
        if (description != null && !description.isEmpty()) {
            JLabel label = new JLabel("<html>" + description + "</html>");
            label.setBorder(BorderFactory.createEmptyBorder(0, 20, 6, 0));
            panel.add(label);
        }
    }

    private static String localized(ExternalUiProtocol.Request request, String key) {
        String value = request.localizedText.get(key);
        return value == null || value.isEmpty() ? key : value;
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}

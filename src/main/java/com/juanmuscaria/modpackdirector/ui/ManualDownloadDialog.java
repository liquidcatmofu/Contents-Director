package com.juanmuscaria.modpackdirector.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dedicated modal UI for user-assisted downloads.
 *
 * Browser/clipboard actions keep this dialog open so the user can wait for the
 * download to finish before selecting the resulting file.
 */
public final class ManualDownloadDialog {
    private ManualDownloadDialog() {
    }

    public static Path show(
        Component parent,
        String url,
        String expectedFileName,
        String target
    ) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame
            ? new JDialog((Frame) owner, "Manual download required", true)
            : owner instanceof Dialog
                ? new JDialog((Dialog) owner, "Manual download required", true)
                : new JDialog((Frame) null, "Manual download required", true);

        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(14, 14, 0, 14));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 10, 0);

        JLabel explanation = new JLabel(
            "<html>Automatic download failed. Open the download page or copy its URL, "
                + "download the file, then select it below.</html>"
        );
        content.add(explanation, constraints);

        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.weightx = 0.0;
        constraints.insets = new Insets(0, 0, 4, 10);
        content.add(new JLabel("Download URL:"), constraints);

        JTextField urlField = readOnlyField(url);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 0, 4, 0);
        content.add(urlField, constraints);

        JPanel urlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton open = new JButton("Open in browser");
        JButton copy = new JButton("Copy URL");
        urlActions.add(open);
        urlActions.add(copy);

        constraints.gridx = 1;
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 12, 0);
        content.add(urlActions, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.insets = new Insets(0, 0, 4, 10);
        content.add(new JLabel("Expected file:"), constraints);

        JTextField expectedField = readOnlyField(expectedFileName);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 0, 4, 0);
        content.add(expectedField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.insets = new Insets(0, 0, 4, 10);
        content.add(new JLabel("Target:"), constraints);

        JTextField targetField = readOnlyField(target);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 0, 8, 0);
        content.add(targetField, constraints);

        JLabel status = new JLabel(" ");
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 0, 0, 0);
        content.add(status, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel = new JButton("Cancel");
        JButton select = new JButton("Select downloaded file...");
        buttons.add(cancel);
        buttons.add(select);

        AtomicReference<Path> selected = new AtomicReference<>();

        open.addActionListener(event -> {
            if (url == null || url.isEmpty()) {
                status.setText("No download URL is available.");
                return;
            }

            try {
                if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                    status.setText("Opened in the default browser.");
                } else {
                    status.setText("Browser opening is unavailable. Copy the URL instead.");
                }
            } catch (Exception e) {
                status.setText("Could not open the browser. Copy the URL instead.");
            }
        });

        copy.addActionListener(event -> {
            if (url == null || url.isEmpty()) {
                status.setText("No download URL is available.");
                return;
            }

            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(url),
                    null
                );
                status.setText("URL copied to the clipboard.");
            } catch (Exception e) {
                status.setText("Could not access the clipboard. Select the URL field and copy it manually.");
                urlField.requestFocusInWindow();
                urlField.selectAll();
            }
        });

        select.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select downloaded file");
            if (expectedFileName != null && !expectedFileName.isEmpty()) {
                chooser.setSelectedFile(new File(expectedFileName));
            }

            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
                selected.set(chooser.getSelectedFile().toPath());
                dialog.dispose();
            }
        });

        cancel.addActionListener(event -> dialog.dispose());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                dialog.dispose();
            }
        });

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(select);

        dialog.pack();
        dialog.setMinimumSize(new Dimension(680, dialog.getHeight()));
        dialog.setSize(Math.max(680, dialog.getWidth()), Math.max(300, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return selected.get();
    }

    private static JTextField readOnlyField(String value) {
        JTextField field = new JTextField(value == null ? "" : value);
        field.setEditable(false);
        field.setFocusable(true);
        field.setCaretPosition(0);
        return field;
    }
}

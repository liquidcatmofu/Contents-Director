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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
        String target,
        Function<String, String> text
    ) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = owner instanceof Frame
            ? new JDialog((Frame) owner, text.apply("modpack_director.manual_download.title"), true)
            : owner instanceof Dialog
                ? new JDialog((Dialog) owner, text.apply("modpack_director.manual_download.title"), true)
                : new JDialog((Frame) null, text.apply("modpack_director.manual_download.title"), true);

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
            "<html>" + text.apply("modpack_director.manual_download.explanation") + "</html>"
        );
        content.add(explanation, constraints);

        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.weightx = 0.0;
        constraints.insets = new Insets(0, 0, 4, 10);
        content.add(new JLabel(text.apply("modpack_director.manual_download.download_url")), constraints);

        JTextField urlField = readOnlyField(url);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 0, 4, 0);
        content.add(urlField, constraints);

        JPanel urlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton open = new JButton(text.apply("modpack_director.manual_download.open_browser"));
        JButton copy = new JButton(text.apply("modpack_director.manual_download.copy_url"));
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
        content.add(new JLabel(text.apply("modpack_director.manual_download.expected_file")), constraints);

        JTextField expectedField = readOnlyField(expectedFileName);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(0, 0, 4, 0);
        content.add(expectedField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.insets = new Insets(0, 0, 4, 10);
        content.add(new JLabel(text.apply("modpack_director.manual_download.target")), constraints);

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

        JPanel detectedPanel = new JPanel(new BorderLayout(8, 0));
        JLabel detected = new JLabel(text.apply("modpack_director.manual_download.waiting"));
        JButton useDetected = new JButton(text.apply("modpack_director.manual_download.use_downloaded_file"));
        useDetected.setEnabled(false);
        detectedPanel.add(detected, BorderLayout.CENTER);
        detectedPanel.add(useDetected, BorderLayout.EAST);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(8, 0, 0, 0);
        content.add(detectedPanel, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel = new JButton(text.apply("modpack_director.manual_download.cancel"));
        JButton select = new JButton(text.apply("modpack_director.manual_download.select_file"));
        buttons.add(cancel);
        buttons.add(select);

        AtomicReference<Path> selected = new AtomicReference<>();
        AtomicReference<Path> detectedFile = new AtomicReference<>();
        DownloadCandidateMonitor downloadMonitor =
            new DownloadCandidateMonitor(expectedFileName, DownloadDirectories.resolve());

        open.addActionListener(event -> {
            if (url == null || url.isEmpty()) {
                status.setText(text.apply("modpack_director.manual_download.no_url"));
                return;
            }

            try {
                if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                    status.setText(text.apply("modpack_director.manual_download.opened_browser"));
                } else {
                    status.setText(text.apply("modpack_director.manual_download.browser_unavailable"));
                }
            } catch (Exception e) {
                status.setText(text.apply("modpack_director.manual_download.browser_failed"));
            }
        });

        copy.addActionListener(event -> {
            if (url == null || url.isEmpty()) {
                status.setText(text.apply("modpack_director.manual_download.no_url"));
                return;
            }

            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(url),
                    null
                );
                status.setText(text.apply("modpack_director.manual_download.copied"));
            } catch (Exception e) {
                status.setText(text.apply("modpack_director.manual_download.clipboard_failed"));
                urlField.requestFocusInWindow();
                urlField.selectAll();
            }
        });

        useDetected.addActionListener(event -> {
            Path candidate = detectedFile.get();
            if (candidate != null) {
                selected.set(candidate);
                dialog.dispose();
            }
        });

        Timer downloadTimer = new Timer(1000, event -> {
            Optional<Path> candidate = downloadMonitor.findStableCandidate();
            if (candidate.isPresent()) {
                Path path = candidate.get();
                detectedFile.set(path);
                detected.setText(text.apply("modpack_director.manual_download.detected") + " " + path);
                detected.setToolTipText(path.toString());
                useDetected.setEnabled(true);
            } else {
                detectedFile.set(null);
                detected.setText(text.apply("modpack_director.manual_download.waiting"));
                detected.setToolTipText(null);
                useDetected.setEnabled(false);
            }
        });
        downloadTimer.setInitialDelay(500);
        downloadTimer.start();

        select.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(text.apply("modpack_director.manual_download.chooser_title"));
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

            @Override
            public void windowClosed(WindowEvent event) {
                downloadTimer.stop();
            }
        });

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(select);

        dialog.pack();
        dialog.setMinimumSize(new Dimension(680, dialog.getHeight()));
        dialog.setSize(Math.max(760, dialog.getWidth()), Math.max(340, dialog.getHeight()));
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

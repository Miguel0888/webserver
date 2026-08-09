package com.aresstack.webserver.ui.publication;

import com.aresstack.webserver.domain.Site;
import com.aresstack.webserver.domain.WebServerConfiguration;
import com.aresstack.webserver.ui.status.PublicationStatus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Liste der Veröffentlichungen ("Published services") mit Add-Aktion.
 */
public class PublicationsPanel extends JPanel {

    public interface Actions {
        void add();

        void edit(Site site);

        void remove(Site site);

        void details(Site site);
    }

    private final JPanel cards = new JPanel();
    private final Actions actions;

    public PublicationsPanel(Actions actions) {
        super(new BorderLayout(0, 8));
        this.actions = actions;
        setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));

        JLabel heading = new JLabel("Published services");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize() + 1f));
        JButton add = new JButton("+ Add service");
        add.addActionListener(e -> actions.add());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(heading, BorderLayout.WEST);
        header.add(add, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        JPanel scrollContent = new JPanel(new BorderLayout());
        scrollContent.add(cards, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(scrollContent);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public void update(WebServerConfiguration configuration, Map<String, PublicationStatus> statuses) {
        cards.removeAll();
        for (Site site : configuration.sites()) {
            PublicationStatus status = statuses.getOrDefault(
                    site.host().value(), PublicationStatus.unknown());
            cards.add(new PublicationCard(site, configuration.defaultUpstream(), status,
                    () -> actions.edit(site),
                    () -> actions.remove(site),
                    () -> actions.details(site)));
        }
        if (configuration.sites().isEmpty()) {
            JLabel empty = new JLabel("No services published yet — add one to get started.");
            empty.setBorder(BorderFactory.createEmptyBorder(24, 4, 24, 4));
            cards.add(empty);
        }
        cards.add(Box.createVerticalStrut(6));
        cards.revalidate();
        cards.repaint();
    }
}

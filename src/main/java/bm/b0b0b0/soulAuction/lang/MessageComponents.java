package bm.b0b0b0.soulAuction.lang;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;

final class MessageComponents {

    private MessageComponents() {
    }

    static Component withoutDefaultItalic(Component component) {
        if (component == null) {
            return Component.empty();
        }
        Component styled = component.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);
        List<Component> children = styled.children();
        if (children.isEmpty()) {
            return styled;
        }
        List<Component> mapped = new ArrayList<>(children.size());
        for (Component child : children) {
            mapped.add(withoutDefaultItalic(child));
        }
        return styled.children(mapped);
    }
}

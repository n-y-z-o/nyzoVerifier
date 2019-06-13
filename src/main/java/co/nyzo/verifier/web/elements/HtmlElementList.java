package co.nyzo.verifier.web.elements;

import java.util.ArrayList;
import java.util.List;

public class HtmlElementList implements HtmlElement {

    private List<HtmlElement> elements = new ArrayList<>();

    public void add(HtmlElement element) {
        elements.add(element);
    }

    @Override
    public String render() {
        StringBuilder result = new StringBuilder();
        for (HtmlElement element : elements) {
            result.append(element.render());
        }

        return result.toString();
    }
}

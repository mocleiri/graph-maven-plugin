package org.kuali.maven.plugins.graph.dot.html;

public class TextItem<T> {
    T element;

    public TextItem() {
        this(null);
    }

    public TextItem(T element) {
        super();
        this.element = element;
    }

    public T getElement() {
        return element;
    }

    public void setElement(T element) {
        this.element = element;
    }
}
package org.jsoup.select;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.util.IdentityHashMap;

/**
 * Base structural evaluator.
 */
abstract class StructuralEvaluator extends Evaluator {
    final Evaluator evaluator;

    public StructuralEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    // Memoize inner matches, to save repeated re-evaluations of parent, sibling etc.
    // root + element: Boolean matches. ThreadLocal in case the Evaluator is compiled then reused across multi threads
    final ThreadLocal<IdentityHashMap<Element, IdentityHashMap<Element, Boolean>>>
        threadMemo = ThreadLocal.withInitial(IdentityHashMap::new);

    boolean memoMatches(final Element root, final Element element) {
        // not using computeIfAbsent, as the lambda impl requires a new Supplier closure object on every hit: tons of GC
        IdentityHashMap<Element, IdentityHashMap<Element, Boolean>> rootMemo = threadMemo.get();
        IdentityHashMap<Element, Boolean> memo = rootMemo.get(root);
        if (memo == null) {
            memo = new IdentityHashMap<>();
            rootMemo.put(root, memo);
        }
        Boolean matches = memo.get(element);
        if (matches == null) {
            matches = evaluator.matches(root, element);
            memo.put(element, matches);
        }
        return matches;
    }

    @Override protected void reset() {
        threadMemo.get().clear();
        super.reset();
    }

    static class Root extends Evaluator {
        @Override
        public boolean matches(Element root, Element element) {
            return root == element;
        }
    }

    static class Has extends StructuralEvaluator {
        final Collector.FirstFinder finder;

        public Has(Evaluator evaluator) {
            super(evaluator);
            finder = new Collector.FirstFinder(evaluator);
        }

        @Override
        public boolean matches(Element root, Element element) {
            // for :has, we only want to match children (or below), not the input element. And we want to minimize GCs
            for (int i = 0; i < element.childNodeSize(); i++) {
                Node node = element.childNode(i);
                if (node instanceof Element) {
                    Element match = finder.find(element, (Element) node);
                    if (match != null)
                        return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format(":has(%s)", evaluator);
        }
    }

    static class Not extends StructuralEvaluator {
        public Not(Evaluator evaluator) {
            super(evaluator);
        }

        @Override
        public boolean matches(Element root, Element element) {
            return !memoMatches(root, element);
        }

        @Override
        public String toString() {
            return String.format(":not(%s)", evaluator);
        }
    }

    static class Parent extends StructuralEvaluator {
        public Parent(Evaluator evaluator) {
            super(evaluator);
        }

        @Override
        public boolean matches(Element root, Element element) {
            if (root == element)
                return false;

            Element parent = element.parent();
            while (parent != null) {
                if (memoMatches(root, parent))
                    return true;
                if (parent == root)
                    break;
                parent = parent.parent();
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("%s ", evaluator);
        }
    }

    static class ImmediateParent extends StructuralEvaluator {
        public ImmediateParent(Evaluator evaluator) {
            super(evaluator);
        }

        @Override
        public boolean matches(Element root, Element element) {
            if (root == element)
                return false;

            Element parent = element.parent();
            return parent != null && memoMatches(root, parent);
        }

        @Override
        public String toString() {
            return String.format("%s > ", evaluator);
        }
    }

    static class PreviousSibling extends StructuralEvaluator {
        public PreviousSibling(Evaluator evaluator) {
            super(evaluator);
        }

        @Override
        public boolean matches(Element root, Element element) {
            final Element parent = element.parent();
            if (root == element || parent == null)
                return false;

            final int size = element.elementSiblingIndex();
            for (int i = 0; i < size; i++) {
                final Element el = parent.child(i);
                boolean matches = memoMatches(root, el);
                if (matches)
                    return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("%s ~ ", evaluator);
        }
    }

    static class ImmediatePreviousSibling extends StructuralEvaluator {
        public ImmediatePreviousSibling(Evaluator evaluator) {
            super(evaluator);
        }

        @Override
        public boolean matches(Element root, Element element) {
            if (root == element)
                return false;

            Element prev = element.previousElementSibling();
            return prev != null && memoMatches(root, prev);
        }

        @Override
        public String toString() {
            return String.format("%s + ", evaluator);
        }
    }
}

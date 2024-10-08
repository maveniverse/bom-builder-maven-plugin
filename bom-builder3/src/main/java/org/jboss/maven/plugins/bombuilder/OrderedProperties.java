package org.jboss.maven.plugins.bombuilder;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** class is not thread safe */
class OrderedProperties extends Properties {
    private final List<Object> keyOrder = new ArrayList<>();

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        t.forEach(this::put);
    }

    @Override
    public Set<Object> keySet() {
        return Collections.synchronizedSet(new KeySet());
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        keyOrder.remove(key);
        keyOrder.add(key);
        return super.put(key, value);
    }

    private class KeySet extends AbstractSet<Object> {
        public Iterator<Object> iterator() {
            return keyOrder.iterator();
        }

        public int size() {
            return keyOrder.size();
        }

        public boolean contains(Object o) {
            return containsKey(o);
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }
    }
}

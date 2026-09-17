package info.jtrac.lucene;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Container for search hits classified by item-level and history-level matches.
 */
public class SearchResultHits implements Serializable {

    private final List<Long> itemIds = new ArrayList<Long>();
    private final List<Long> historyIds = new ArrayList<Long>();
    private final List<Long> itemLevelHitItemIds = new ArrayList<Long>();

    public List<Long> getItemIds() {
        return itemIds;
    }

    public List<Long> getHistoryIds() {
        return historyIds;
    }

    public List<Long> getItemLevelHitItemIds() {
        return itemLevelHitItemIds;
    }

    public boolean isEmpty() {
        return itemIds.isEmpty() && historyIds.isEmpty();
    }
}

package edu.stanford.thingengine.engine.ui;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by gcampagn on 10/17/16.
 */
public class AutoCompletionAdapter extends BaseAdapter implements Filterable {
    public static class Item {
        public final String display;
        public final String utterance;
        public final String targetCode;
        public final boolean hasSlots;

        public Item(String display, String utterance, String targetCode, boolean hasSlots) {
            this.display = display;
            this.utterance = utterance;
            this.targetCode = targetCode;
            this.hasSlots = hasSlots;
        }
    }

    private static boolean hasSlots(String[] tokens) {
        for (String tok : tokens) {
            if (tok.startsWith("$") && !"$".equals(tok))
                return true;
        }
        return true;
    }

    private class CompletionFilter extends Filter {
        private class Example {
            String utterance;
            String targetCode;
            String[] tokens;
            boolean hasSlots;
        }

        private List<Example> jsonToExamples(JSONArray json) throws JSONException {
            List<Example> list = new ArrayList<>();

            Set<String> programs = new HashSet<>();
            for (int i = 0; i < json.length(); i++) {
                JSONObject obj = json.getJSONObject(i);
                Example ex = new Example();
                ex.targetCode = obj.getString("target_code");
                if (programs.contains(ex.targetCode))
                    continue;
                programs.add(ex.targetCode);

                ex.utterance = obj.getString("utterance");
                ex.tokens = ex.utterance.split("\\s+");
                ex.hasSlots = hasSlots(ex.tokens);
                list.add(ex);
            }

            return list.subList(0, Math.min(5, list.size()));
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            try {
                if (constraint == null)
                    return new FilterResults();
                String key = constraint.toString();
                List<Example> examples = jsonToExamples(tp.getExamplesByKey(key));

                FilterResults results = new FilterResults();
                results.count = examples.size();
                results.values = examples;
                return results;
            } catch(IOException| JSONException e) {
                Log.e(MainActivity.LOG_TAG, "Failed to retrieve suggestions from ThingPedia", e);
                return new FilterResults();
            }
        }

        private String presentExample(String[] tokens) {
            StringBuilder builder = new StringBuilder();

            boolean first = true;
            for (String token : tokens) {
                if (!first)
                    builder.append(" ");
                first = false;
                if (token.startsWith("$"))
                    builder.append("____");
                else
                    builder.append(token);
            }

            return builder.toString();
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (results.count == 0) {
                store.clear();
                notifyDataSetChanged();
                return;
            }

            store.clear();
            List<?> list = (List<?>) results.values;
            for (Object o : list) {
                Example ex = (Example)o;
                Item item = new Item(presentExample(ex.tokens), ex.utterance, ex.targetCode, ex.hasSlots);
                store.add(item);
            }

            notifyDataSetChanged();
        }
    }

    private final ThingpediaClient tp;
    private final Context ctx;
    private final List<Item> store = new ArrayList<>();
    private final Filter filter = new CompletionFilter();

    public AutoCompletionAdapter(ThingpediaClient tp, Context ctx) {
        this.tp = tp;
        this.ctx = ctx;
    }

    @Override
    public int getCount() {
        return store.size();
    }

    @Override
    public Item getItem(int position) {
        return store.get(position);
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Item item = getItem(position);

        if (convertView != null && convertView instanceof TextView) {
            ((TextView)convertView).setText(item.display);
            return convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            TextView textView = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, null);
            textView.setText(item.display);
            return textView;
        }
    }

    @Override
    public Filter getFilter() {
        return filter;
    }
}

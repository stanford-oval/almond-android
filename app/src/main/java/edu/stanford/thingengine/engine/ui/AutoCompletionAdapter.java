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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Created by gcampagn on 10/17/16.
 */
public class AutoCompletionAdapter extends BaseAdapter implements Filterable {
    // this is the same list of stop words SEMPRE uses
    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        Collections.addAll(STOP_WORDS, "in", "on", "a", "to", "with", "and",
                "\'", "\"", "``", "`", "\'\'", "a", "an", "the", "that", "which",
                ".", "what", "?", "is", "are", "am", "be", "of");
    }

    public static class Item {
        public final String display;
        public final String utterance;
        public final String targetJson;

        public Item(String display, String utterance, String targetJson) {
            this.display = display;
            this.utterance = utterance;
            this.targetJson = targetJson;
        }
    }

    private class CompletionFilter extends Filter {
        private List<String> tokenize(String string) {
            String[] tokens = string.split("(\\s+|[,\\.\"\'])");
            List<String> filtered = new ArrayList<>();

            for (String token : tokens) {
                if (token.trim().isEmpty())
                    continue;

                filtered.add(token.toLowerCase());
            }
            return filtered;
        }

        private class Example {
            public String utterance;
            public List<String> tokens;
            public String targetJson;
            public String kind;
            public double score;
        }

        private void scoreExample(Example example, Set<String> keyTokens) {
           example.tokens = tokenize(example.utterance);

            // score is 2 for finding the right device kind,
            // 1 for each matched word and 0.5 for each matched
            // argument name

            double score = keyTokens.contains(example.kind) ? 2 : 0;

            for (String t : example.tokens) {
                if (t.startsWith("$")) {
                    if (keyTokens.contains(t.substring(1)))
                        score += 0.5;
                } else {
                    if (keyTokens.contains(t))
                        score += 1;
                }
            }

            example.score = score;
        }

        private List<Example> sortAndFilterExamples(String raw, List<Example> examples) {
            ListIterator<Example> li = examples.listIterator();

            Set<String> jsons = new HashSet<>();
            while (li.hasNext()) {
                Example ex = li.next();
                if (jsons.contains(ex.targetJson)) {
                    li.remove();
                } else {
                    jsons.add(ex.targetJson);
                }
            }

            Set<String> keyTokens = new HashSet<>(tokenize(raw));
            for (Example ex : examples) {
                scoreExample(ex, keyTokens);
            }

            // find max score, then find all examples with max score
            // this lets us use the most words in what the user said,
            // and increases the opportunity for a "did you mean foo?"
            // question
            double maxScore = Double.NEGATIVE_INFINITY;
            for (Example ex : examples)
                maxScore = Math.max(ex.score, maxScore);

            List<Example> filtered = new ArrayList<>();
            for (Example ex : examples) {
                if (ex.score == maxScore)
                    filtered.add(ex);
                if (filtered.size() >= 5)
                    break;
            }
            return filtered;
        }

        private List<Example> jsonToExamples(JSONArray json) throws JSONException {
            List<Example> list = new LinkedList<>();

            for (int i = 0; i < json.length(); i++) {
                JSONObject obj = json.getJSONObject(i);
                Example ex = new Example();
                ex.utterance = obj.getString("utterance");
                ex.targetJson = obj.getString("target_json");
                ex.kind = obj.getString("kind");
                list.add(ex);
            }

            return list;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            try {
                if (constraint == null)
                    return new FilterResults();
                String key = constraint.toString();
                List<Example> examples = sortAndFilterExamples(key, jsonToExamples(tp.getExamplesByKey(key)));

                FilterResults results = new FilterResults();
                results.count = examples.size();
                results.values = examples;
                return results;
            } catch(IOException| JSONException e) {
                Log.e(MainActivity.LOG_TAG, "Failed to retrieve suggestions from ThingPedia", e);
                return new FilterResults();
            }
        }

        private String presentExample(List<String> tokens) {
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
                Item item = new Item(presentExample(ex.tokens), ex.utterance, ex.targetJson);
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

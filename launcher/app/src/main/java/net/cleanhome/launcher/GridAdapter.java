package net.cleanhome.launcher;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The 4-column app grid. Tiles are bound lazily; banners arrive from BannerLoader. */
public class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {

    public interface Listener {
        void onTileClick(AppEntry app);
        void onTileLongClick(AppEntry app);
        void onTileFocus(int position, TileView tile);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TileView tile;
        Holder(TileView t) { super(t); tile = t; }
    }

    private final List<AppEntry> items = new ArrayList<>();
    private final BannerLoader loader;
    private final Listener listener;
    private final int tileW, tileH, gap;

    public GridAdapter(BannerLoader loader, Listener l, int tileW, int tileH, int gap) {
        this.loader = loader; this.listener = l; this.tileW = tileW; this.tileH = tileH; this.gap = gap;
        setHasStableIds(true);
    }

    public void setItems(List<AppEntry> list) {
        items.clear(); items.addAll(list); notifyDataSetChanged();
    }

    public AppEntry get(int i) { return items.get(i); }
    public boolean sameItems(List<AppEntry> list) { return items.equals(list); }
    @Override public int getItemCount() { return items.size(); }
    @Override public long getItemId(int position) { return items.get(position).component.hashCode(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TileView t = (TileView) LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tile, parent, false);
        t.setTileSize(tileW, tileH);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
        t.setLayoutParams(new RecyclerView.LayoutParams(lp));
        return new Holder(t);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        final AppEntry app = items.get(position);
        final TileView t = h.tile;
        t.app = app;
        t.label.setText(app.label);
        Bitmap cached = loader.cached(app);
        t.banner.setImageBitmap(cached);
        if (cached == null) {
            loader.load(app, (a, bmp, accent) -> { if (t.app == a && bmp != null) t.banner.setImageBitmap(bmp); });
        }
        t.setOnClickListener(v -> listener.onTileClick(app));
        t.setOnLongClickListener(v -> { listener.onTileLongClick(app); return true; });
        t.setOnFocusChangeListener((v, has) -> {
            // TileView's own listener was replaced; re-apply its visual change through a tiny proxy.
            t.onFocusProxy(has);
            if (has) listener.onTileFocus(h.getBindingAdapterPosition(), t);
        });
    }
}

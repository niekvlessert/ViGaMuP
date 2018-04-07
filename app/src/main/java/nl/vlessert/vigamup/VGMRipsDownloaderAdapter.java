package nl.vlessert.vigamup;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.cache.memory.impl.WeakMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by niek on 02/09/2017.
 */

public class VGMRipsDownloaderAdapter extends RecyclerView.Adapter<VGMRipsDownloaderAdapter.ViewHolder> {
    //private List<String> values;
    private Context mContext;
    private JSONArray json;

    ImageLoaderConfiguration config;
    ImageLoader imageLoader;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private TextView chip;
        private TextView tracks;
        private TextView length;
        private TextView composer;
        private TextView system;
        private ImageView imageView;
        private View layout;

        public ViewHolder(View v) {
            super(v);
            layout = v;
            title = (TextView) v.findViewById(R.id.title);
            chip = (TextView) v.findViewById(R.id.chip);
            tracks = (TextView) v.findViewById(R.id.tracks);
            length = (TextView) v.findViewById(R.id.length);
            composer = (TextView) v.findViewById(R.id.composer);
            system = (TextView) v.findViewById(R.id.system);
            imageView = (ImageView) v.findViewById(R.id.icon);
        }
    }

    public void add(int position, String item) {
        //values.add(position, item);
        notifyItemInserted(position);
    }

    public void remove(int position) {
        //values.remove(position);
        notifyItemRemoved(position);
    }

    public void hide(int position){
        Log.d("blerp", "hide!!");
        //values.remove(position);
        notifyItemRemoved(position);
    }

    public void updateData(JSONArray j){
        json = j;
        notifyDataSetChanged();
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public VGMRipsDownloaderAdapter(Context context, JSONArray j) {
        //values = myDataset;
        mContext = context;
        json = j;

        DisplayImageOptions defaultOptions = new DisplayImageOptions.Builder()
                .resetViewBeforeLoading(true)
                .cacheOnDisk(true)
                .cacheInMemory(true)
                .imageScaleType(ImageScaleType.EXACTLY)
                .displayer(new FadeInBitmapDisplayer(300))
                .build();

        config = new ImageLoaderConfiguration.Builder(mContext)
                .defaultDisplayImageOptions(defaultOptions)
                .memoryCache(new WeakMemoryCache())
                .diskCacheSize(100 * 1024 * 1024)
                .build();

        ImageLoader.getInstance().init(config);
        imageLoader = ImageLoader.getInstance();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public VGMRipsDownloaderAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(R.layout.vgmrips_downloader_adapter, parent, false);
        // set the view's size, margins, paddings and layout parameters
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        //final String name = values.get(position);
        String imgUrl = "";
        JSONObject obj;

        try {
            obj = json.getJSONObject(position);
            //Log.d("lazyloading", obj.getString("topic_title"));
            imgUrl = obj.getString("zip_url");
            holder.title.setText(obj.getString("topic_title"));
            holder.chip.setText("Chips: "+obj.getString("Sound Chips"));
            holder.tracks.setText("Tracks: "+obj.getString("Tracks"));
            holder.length.setText("Length: "+obj.getString("Playing time"));
            holder.composer.setText("Composer(s): "+obj.getString("Composer"));
            holder.system.setText("System: "+obj.getString("System"));

        } catch (JSONException e){}

        holder.title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("lazyloader","blerp");
                //remove(position);
            }
        });

        if (imgUrl.length()!=0) {
            imgUrl = imgUrl.substring(0, imgUrl.lastIndexOf(".")).concat(".png");
            Log.d("lazyloading", imgUrl);
            imageLoader.displayImage(imgUrl, holder.imageView);
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return json.length();
    }

}


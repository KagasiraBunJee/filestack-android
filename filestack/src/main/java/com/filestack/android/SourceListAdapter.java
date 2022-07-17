package com.filestack.android;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.filestack.android.internal.SourceInfo;

import java.util.ArrayList;

interface SourceSelectionListener {
    void onSourceSelected(String id);
}

public class SourceListAdapter extends RecyclerView.Adapter<SourceListAdapter.ViewHolder> {
    private final ArrayList<SourceInfo> sources;
    private final SourceSelectionListener listener;
    private final Theme theme;

    public SourceListAdapter(ArrayList<SourceInfo> sources, SourceSelectionListener listener, Theme theme) {
        this.sources = sources;
        this.listener = listener;
        this.theme = theme;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View listItem= layoutInflater.inflate(R.layout.filestack__sources_list_item, parent, false);
        ViewHolder viewHolder = new ViewHolder(listItem);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final SourceInfo sourceInfo = sources.get(position);
        holder.textView.setText(sources.get(position).getTextId());
        holder.textView.setTextColor(theme.getItemTextColor());
        holder.imageView.setImageResource(sources.get(position).getIconId());
        holder.relativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onSourceSelected(sourceInfo.getId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public TextView textView;
        public RelativeLayout relativeLayout;
        public ViewHolder(View itemView) {
            super(itemView);
            this.imageView = (ImageView) itemView.findViewById(R.id.sourceImage);
            this.textView = (TextView) itemView.findViewById(R.id.sourceLabel);
            relativeLayout = (RelativeLayout)itemView.findViewById(R.id.relativeLayout);
        }
    }
}

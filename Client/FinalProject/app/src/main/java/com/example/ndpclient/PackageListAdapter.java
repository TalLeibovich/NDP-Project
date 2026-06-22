package com.example.ndpclient;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

public class PackageListAdapter extends ArrayAdapter<PackageItem> {

    // Creates an adapter for rendering package rows.
    public PackageListAdapter(Context context, List<PackageItem> packages) {
        super(context, 0, packages);
    }

    // Binds package data into a list row view.
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        PackageItem item = getItem(position);
        if (item == null) {
            return super.getView(position, convertView, parent);
        }

        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_package_row, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.tvPackageId.setText(item.getId());

        String bestAddress = item.getBestAddress();
        holder.tvPackageAddress.setText(bestAddress == null
                ? "Address: not available"
                : "Address: " + bestAddress);

        holder.tvPackageDeadline.setText("deadline: " + item.getDeadline());
        holder.tvPackageWeight.setText(String.format(Locale.US, "weight: %.1f", item.getWeight()));
        holder.tvPackageVolume.setText(String.format(Locale.US, "volume: %.1f", item.getVolume()));
        holder.tvPackageProfit.setText(String.format(Locale.US, "profit: %.1f", item.getProfit()));

        return convertView;
    }

    private static class ViewHolder {
        final TextView tvPackageId;
        final TextView tvPackageAddress;
        final TextView tvPackageDeadline;
        final TextView tvPackageWeight;
        final TextView tvPackageVolume;
        final TextView tvPackageProfit;

        // Stores row views to avoid repeated lookups.
        ViewHolder(View view) {
            tvPackageId = view.findViewById(R.id.tvPackageId);
            tvPackageAddress = view.findViewById(R.id.tvPackageAddress);
            tvPackageDeadline = view.findViewById(R.id.tvPackageDeadline);
            tvPackageWeight = view.findViewById(R.id.tvPackageWeight);
            tvPackageVolume = view.findViewById(R.id.tvPackageVolume);
            tvPackageProfit = view.findViewById(R.id.tvPackageProfit);
        }
    }
}
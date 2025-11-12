package com.example.healthapp;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;

public class UserRecordAdapter extends RecyclerView.Adapter<UserRecordAdapter.ViewHolder> {
    private ArrayList<String> users;
    private ArrayList<JSONObject> userObjects;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public UserRecordAdapter(ArrayList<String> users, ArrayList<JSONObject> userObjects, OnItemClickListener listener) {
        this.users = users;
        this.userObjects = userObjects;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.user_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String userText = users.get(position);
        holder.textView.setText(userText);
        Log.d("UserRecordAdapter", "Binding item at position " + position + ": " + userText);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
    }

    @Override
    public int getItemCount() {
        Log.d("UserRecordAdapter", "getItemCount: " + users.size());
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }

    public void updateData(ArrayList<String> newUsers, ArrayList<JSONObject> newUserObjects) {
        if (newUsers == null || newUserObjects == null) {
            Log.e("UserRecordAdapter", "updateData: newUsers or newUserObjects is null");
            return;
        }
        Log.d("UserRecordAdapter", "Updating data with " + newUsers.size() + " items");
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return users.size();
            }

            @Override
            public int getNewListSize() {
                return newUsers.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return users.get(oldItemPosition).equals(newUsers.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return users.get(oldItemPosition).equals(newUsers.get(newItemPosition));
            }
        });
        users.clear();
        users.addAll(newUsers);
        userObjects.clear();
        userObjects.addAll(newUserObjects);
        diffResult.dispatchUpdatesTo(this);
        notifyDataSetChanged(); // Fallback to ensure update
        Log.d("UserRecordAdapter", "Data updated, notified adapter");
    }
}
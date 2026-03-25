package com.example.testrepo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {
    private final ArrayList<ReceiptHistoryStore.HistoryEntry> historyEntries = new ArrayList<>();
    private HistoryEntriesAdapter historyEntriesAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        View backButton = findViewById(R.id.button_back);
        ListView historyListView = findViewById(R.id.list_history_receipts);

        historyEntriesAdapter = new HistoryEntriesAdapter();
        historyListView.setAdapter(historyEntriesAdapter);
        historyListView.setEmptyView(findViewById(R.id.text_history_empty));
        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            ReceiptHistoryStore.HistoryEntry entry = historyEntriesAdapter.getItem(position);
            if (entry != null) {
                showHistoryDetailsDialog(entry);
            }
        });
        backButton.setOnClickListener(view -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistoryEntries();
    }

    private void loadHistoryEntries() {
        historyEntries.clear();
        historyEntries.addAll(ReceiptHistoryStore.loadEntries(this));
        historyEntriesAdapter.notifyDataSetChanged();
    }

    private void showHistoryDetailsDialog(@NonNull ReceiptHistoryStore.HistoryEntry entry) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_history_receipt_details, null);
        TextView titleView = dialogView.findViewById(R.id.text_history_receipt_dialog_title);
        LinearLayout participantsLayout =
                dialogView.findViewById(R.id.layout_history_receipt_participants);

        titleView.setText(entry.receiptName);
        participantsLayout.removeAllViews();

        if (entry.participants.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.history_no_participants);
            participantsLayout.addView(emptyView);
        } else {
            for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
                View rowView = LayoutInflater.from(this).inflate(
                        R.layout.item_history_participant,
                        participantsLayout,
                        false
                );
                TextView nameView = rowView.findViewById(R.id.text_history_participant_name);
                TextView amountView = rowView.findViewById(R.id.text_history_participant_amount);

                nameView.setText(participant.name);
                amountView.setText(participant.amount);
                participantsLayout.addView(rowView);
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.show();
    }

    private final class HistoryEntriesAdapter extends ArrayAdapter<ReceiptHistoryStore.HistoryEntry> {
        private HistoryEntriesAdapter() {
            super(HistoryActivity.this, R.layout.item_history_receipt, historyEntries);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_history_receipt, parent, false);
            }

            ReceiptHistoryStore.HistoryEntry entry = getItem(position);
            TextView receiptNameView = itemView.findViewById(R.id.text_history_receipt_name);
            TextView totalAmountView = itemView.findViewById(R.id.text_history_receipt_total);
            TextView sentDateView = itemView.findViewById(R.id.text_history_receipt_date);

            if (entry != null) {
                receiptNameView.setText(entry.receiptName);
                totalAmountView.setText(entry.totalAmount);
                sentDateView.setText(entry.sentDate);
            }

            return itemView;
        }
    }
}

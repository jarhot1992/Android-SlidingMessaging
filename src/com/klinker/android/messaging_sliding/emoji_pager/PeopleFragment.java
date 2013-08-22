package com.klinker.android.messaging_sliding.emoji_pager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.klinker.android.messaging_donate.R;
import com.klinker.android.messaging_sliding.MainActivity;
import com.klinker.android.messaging_sliding.emojis.EmojiAdapter2;
import com.klinker.android.messaging_sliding.emojis.EmojiConverter2;
import com.tonicartos.widget.stickygridheaders.StickyGridHeadersGridView;

public class PeopleFragment extends Fragment {

    private static final String ARG_POSITION = "position";

    private int position;

    public static PeopleFragment newInstance(int position) {
        PeopleFragment f = new PeopleFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_POSITION, position);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        position = getArguments().getInt(ARG_POSITION);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final GridView emojiGrid = new GridView(getActivity());

        emojiGrid.setColumnWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics()));
        emojiGrid.setNumColumns(GridView.AUTO_FIT);

        if(position == 0)
            emojiGrid.setAdapter(new PeopleEmojiAdapter2(getActivity()));
        else if (position == 1)
            emojiGrid.setAdapter(new ThingsEmojiAdapter2(getActivity()));
        else if (position == 2)
            emojiGrid.setAdapter(new NatureEmojiAdapter2(getActivity()));
        else if (position == 3)
            emojiGrid.setAdapter(new TransEmojiAdapter2(getActivity()));
        else
            emojiGrid.setAdapter(new OtherEmojiAdapter2(getActivity()));

        emojiGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View v, int position, long id)
            {
                MainActivity.insertEmoji(EmojiAdapter2.mEmojiTexts[position]);
            }
        });

        return emojiGrid;

        /*FrameLayout fl = new FrameLayout(getActivity());
        fl.setLayoutParams(params);

        final int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources()
                .getDisplayMetrics());

        TextView v = new TextView(getActivity());
        params.setMargins(margin, margin, margin, margin);
        v.setLayoutParams(params);
        v.setLayoutParams(params);
        v.setGravity(Gravity.CENTER);
        v.setText("CARD " + (position + 1));

        fl.addView(v);
        return fl;*/
    }

}
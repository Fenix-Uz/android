package org.fenixuz.ui.bots;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.fenixuz.utils.LanguageCode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Novagram bots — a curated, hardcoded list of the project's own bots, grouped by category.
 * Tapping a row opens that bot's chat (native START button); nothing is auto-sent.
 *
 * Rendering: each row shows the bot avatar + name + @username. Names/avatars are pulled from
 * Telegram lazily — a colored-initials placeholder shows instantly (works offline), and the real
 * avatar fills in once the username resolves. Resolution is per-visible-row (lazy) and de-duped, so
 * opening the screen never floods the server with 30+ resolve requests at once.
 */
public class BotsActivity extends BaseFragment {

    private RecyclerListView listView;
    private Adapter adapter;

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_BOT = 1;

    private final ArrayList<Item> items = new ArrayList<>();
    // Usernames already handed to the resolver, so a rebind never re-requests the same one.
    private final HashSet<String> requested = new HashSet<>();

    private static class Item {
        final int viewType;
        final int headerCode;
        final String username;
        final String name;
        boolean divider;

        private Item(int viewType, int headerCode, String username, String name, boolean divider) {
            this.viewType = viewType;
            this.headerCode = headerCode;
            this.username = username;
            this.name = name;
            this.divider = divider;
        }

        static Item header(int headerCode) {
            return new Item(VIEW_TYPE_HEADER, headerCode, null, null, false);
        }

        static Item bot(String username, String name, boolean divider) {
            return new Item(VIEW_TYPE_BOT, 0, username, name, divider);
        }
    }

    // Curated bot list — usernames verbatim as provided, grouped into categories.
    private void buildRows() {
        items.clear();
        addGroup(347, new String[][]{ // Media & downloads
                {"novogram_youtube_bot", "YouTube"},
                {"novagram_instagram_bot", "Instagram"},
                {"novagram_tiktok_bot", "TikTok"},
                {"novagram_facebook_bot", "Facebook"},
                {"novagram_shazam_bot", "Shazam"},
                {"novagram_spotify_bot", "Spotify"},
                {"novagram_vk_bot", "VK"},
                {"novagram_rutube_bot", "RuTube"},
                {"novagram_twitch_bot", "Twitch"},
                {"novagram_pinterest_bot", "Pinterest"},
                {"novagram_reddit_bot", "Reddit"},
                {"novagram_twitter_bot", "Twitter"},
        });
        addGroup(348, new String[][]{ // Media editing
                {"novagram_compress_bot", "Compress"},
                {"novagram_crop_bot", "Crop"},
                {"novagram_trimmer_bot", "Trimmer"},
                {"novagram_blur_bot", "Blur"},
                {"novagram_remover_bot", "Remover"},
                {"novagram_upscaler_bot", "Upscaler"},
                {"novagram_sticker_bot", "Sticker"},
                {"novagram_voice_bot", "Voice"},
        });
        addGroup(349, new String[][]{ // Tools
                {"novagram_qr_bot", "QR"},
                {"novagram_password_bot", "Password"},
                {"novagram_tempmail_bot", "Temp Mail"},
                {"novagram_pdf_bot", "PDF"},
                {"novagram_tts_bot", "TTS"},
        });
        addGroup(350, new String[][]{ // Useful & fun
                {"novagram_meme_bot", "Meme"},
                {"novagram_translit_bot", "Translit"},
                {"novagram_wallpaper_bot", "Wallpaper"},
                {"novagram_movies_bot", "Movies"},
                {"novagram_weather_bot", "Weather"},
                {"novagram_rates_bot", "Rates"},
                {"novagram_facts_bot", "Facts"},
                {"novagram_quotes_bot", "Quotes"},
                {"novagram_proverbs_bot", "Proverbs"},
        });
    }

    private void addGroup(int headerCode, String[][] bots) {
        items.add(Item.header(headerCode));
        for (int i = 0; i < bots.length; i++) {
            items.add(Item.bot(bots[i][0], bots[i][1], i < bots.length - 1));
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(LanguageCode.INSTANCE.getMyTitles(344));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        buildRows();

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new Adapter());
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            Item item = items.get(position);
            if (item.viewType == VIEW_TYPE_BOT && item.username != null) {
                // Resolves the username (spinner if not cached) and opens the bot chat; the bot
                // override inside openByUserName forces the chat (never the profile).
                getMessagesController().openByUserName(item.username, BotsActivity.this, 0);
            }
        });

        return fragmentView;
    }

    private int indexOfUsername(String username) {
        for (int i = 0; i < items.size(); i++) {
            if (username.equals(items.get(i).username)) {
                return i;
            }
        }
        return -1;
    }

    private void ensureResolved(String username) {
        if (requested.contains(username)) {
            return;
        }
        requested.add(username);
        getMessagesController().getUserNameResolver().resolve(username, peerId ->
            AndroidUtilities.runOnUIThread(() -> {
                if (peerId == null || peerId <= 0 || adapter == null) {
                    return;
                }
                int pos = indexOfUsername(username);
                if (pos >= 0) {
                    adapter.notifyItemChanged(pos);
                }
            })
        );
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new GraySectionCell(parent.getContext());
            } else {
                UserCell cell = new UserCell(parent.getContext(), 0, 0, false, false);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                view = cell;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (item.viewType == VIEW_TYPE_HEADER) {
                ((GraySectionCell) holder.itemView).setText(LanguageCode.INSTANCE.getMyTitles(item.headerCode));
            } else {
                UserCell cell = (UserCell) holder.itemView;
                TLRPC.User user = getMessagesController().getUser(item.username);
                if (user != null) {
                    // Resolved: real avatar + real name; show @username as the subtitle.
                    cell.setData(user, null, "@" + item.username, 0, item.divider);
                } else {
                    // Not yet resolved: colored-initials placeholder from the display name.
                    cell.setData(null, item.name, "@" + item.username, 0, item.divider);
                    ensureResolved(item.username);
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            // Only bot rows are tappable; section headers are not.
            return holder.getItemViewType() == VIEW_TYPE_BOT;
        }
    }
}

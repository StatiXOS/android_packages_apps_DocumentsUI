/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.dirlist;

import static com.android.documentsui.State.ACTION_BROWSE;
import static com.android.documentsui.State.ACTION_CREATE;
import static com.android.documentsui.State.ACTION_GET_CONTENT;
import static com.android.documentsui.State.ACTION_OPEN;
import static com.android.documentsui.State.ACTION_OPEN_TREE;
import static com.android.documentsui.State.ACTION_PICK_COPY_DESTINATION;

import android.content.Context;
import android.provider.DocumentsContract.Document;
import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.Menus;
import com.android.documentsui.MimePredicate;
import com.android.documentsui.R;
import com.android.documentsui.State;
import com.android.documentsui.dirlist.DirectoryFragment.ResultType;

/**
 * Providers support for specializing the DirectoryFragment to the "host" Activity.
 * Feel free to expand the role of this class to handle other specializations.
 */
public abstract class FragmentTuner {

    final Context mContext;
    final State mState;

    public FragmentTuner(Context context, State state) {
        mContext = context;
        mState = state;
    }

    public static FragmentTuner pick(Context context, State state) {
        switch (state.action) {
            case ACTION_BROWSE:
                return new FilesTuner(context, state);
            default:
                return new DocumentsTuner(context, state);
        }
    }

    public abstract void updateActionMenu(
            Menu menu, @ResultType int dirType,
            boolean canCopy, boolean canDelete, boolean canRename);

    // Subtly different from isDocumentEnabled. The reason may be illuminated as follows.
    // A folder is enabled such that it may be double clicked, even in settings
    // when the folder itself cannot be selected. This may also be true of container types.
    public boolean canSelectType(String docMimeType, int docFlags) {
        return true;
    }

    public boolean isDocumentEnabled(String docMimeType, int docFlags) {
        if (isDirectory(docMimeType)) {
            return true;
        }

        return MimePredicate.mimeMatches(mState.acceptMimes, docMimeType);
    }

    abstract void onModelLoaded(Model model, @ResultType int resultType, boolean isSearch);

    /**
     * When managed mode is enabled, active downloads will be visible in the UI.
     * Presumably this should only be true when in the downloads directory.
     */
    abstract boolean enableManagedMode();

    /**
     * Provides support for Platform specific specializations of DirectoryFragment.
     */
    private static final class DocumentsTuner extends FragmentTuner {

        public DocumentsTuner(Context context, State state) {
            super(context, state);
        }

        @Override
        public boolean canSelectType(String docMimeType, int docFlags) {
            if (!isDocumentEnabled(docMimeType, docFlags)) {
                return false;
            }

            if (isDirectory(docMimeType)) {
                return false;
            }

            if (mState.action == ACTION_OPEN_TREE
                    || mState.action == ACTION_PICK_COPY_DESTINATION) {
                // In this case nothing *ever* is selectable...the expected user behavior is
                // they navigate *into* a folder, then click a confirmation button indicating
                // that the current directory is the directory they are picking.
                return false;
            }

            return true;
        }

        @Override
        public boolean isDocumentEnabled(String docMimeType, int docFlags) {
            // Directories are always enabled.
            if (isDirectory(docMimeType)) {
                return true;
            }

            switch (mState.action) {
                case ACTION_CREATE:
                    // Read-only files are disabled when creating.
                    if ((docFlags & Document.FLAG_SUPPORTS_WRITE) == 0) {
                        return false;
                    }
                case ACTION_OPEN:
                case ACTION_GET_CONTENT:
                    final boolean isVirtual = (docFlags & Document.FLAG_VIRTUAL_DOCUMENT) != 0;
                    if (isVirtual && mState.openableOnly) {
                        return false;
                    }
            }

            return MimePredicate.mimeMatches(mState.acceptMimes, docMimeType);
        }

        @Override
        public void updateActionMenu(
                Menu menu, @ResultType int dirType,
                boolean canCopy, boolean canDelete, boolean canRename) {

            MenuItem open = menu.findItem(R.id.menu_open);
            MenuItem share = menu.findItem(R.id.menu_share);
            MenuItem delete = menu.findItem(R.id.menu_delete);
            MenuItem rename = menu.findItem(R.id.menu_rename);
            MenuItem selectAll = menu.findItem(R.id.menu_select_all);

            open.setVisible(true);
            share.setVisible(false);
            delete.setVisible(false);
            rename.setVisible(false);
            selectAll.setVisible(mState.allowMultiple);

            Menus.disableHiddenItems(menu);
        }

        @Override
        void onModelLoaded(Model model, @ResultType int resultType, boolean isSearch) {
            boolean showDrawer = false;

            if (MimePredicate.mimeMatches(MimePredicate.VISUAL_MIMES, mState.acceptMimes)) {
                showDrawer = false;
            }
            if (mState.external && mState.action == ACTION_GET_CONTENT) {
                showDrawer = true;
            }
            if (mState.action == ACTION_PICK_COPY_DESTINATION) {
                showDrawer = true;
            }

            // When launched into empty root, open drawer.
            if (model.isEmpty()) {
                showDrawer = true;
            }

            if (showDrawer && !mState.hasInitialLocationChanged() && !isSearch) {
                // This noops on layouts without drawer, so no need to guard.
                ((BaseActivity) mContext).setRootsDrawerOpen(true);
            }
        }

        @Override
        public boolean enableManagedMode() {
            return false;
        }
    }

    /**
     * Provides support for Files activity specific specializations of DirectoryFragment.
     */
    private static final class FilesTuner extends FragmentTuner {

        public FilesTuner(Context context, State state) {
            super(context, state);
        }

        @Override
        public void updateActionMenu(
                Menu menu, @ResultType int dirType,
                boolean canCopy, boolean canDelete, boolean canRename) {

            MenuItem copy = menu.findItem(R.id.menu_copy_to_clipboard);
            MenuItem paste = menu.findItem(R.id.menu_paste_from_clipboard);
            copy.setEnabled(canCopy);

            MenuItem rename = menu.findItem(R.id.menu_rename);
            MenuItem moveTo = menu.findItem(R.id.menu_move_to);
            MenuItem copyTo = menu.findItem(R.id.menu_copy_to);

            copyTo.setVisible(true);
            moveTo.setVisible(true);
            rename.setVisible(true);

            copyTo.setEnabled(canCopy);
            moveTo.setEnabled(canCopy && canDelete);
            rename.setEnabled(canRename);

            menu.findItem(R.id.menu_share).setVisible(true);
            menu.findItem(R.id.menu_delete).setVisible(canDelete);
            menu.findItem(R.id.menu_open).setVisible(false);

            Menus.disableHiddenItems(menu, copy, paste);
        }

        @Override
        void onModelLoaded(Model model, @ResultType int resultType, boolean isSearch) {
            // When launched into empty root, open drawer.
            if (model.isEmpty() && !mState.hasInitialLocationChanged() && !isSearch) {
                // This noops on layouts without drawer, so no need to guard.
                ((BaseActivity) mContext).setRootsDrawerOpen(true);
            }
        }

        @Override
        public boolean enableManagedMode() {
            // When in downloads top level directory, we also show active downloads.
            // And while we don't allow folders in Downloads, we do allow Zip files in
            // downloads that themselves can be opened and viewed like directories.
            // This method helps us understand when to kick in on those special behaviors.
            return mState.stack.root != null
                    && mState.stack.root.isDownloads()
                    && mState.stack.size() == 1;
        }
    }

    private static boolean isDirectory(String mimeType) {
        return Document.MIME_TYPE_DIR.equals(mimeType);
    }
}

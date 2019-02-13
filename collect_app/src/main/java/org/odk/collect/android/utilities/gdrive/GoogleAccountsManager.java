/*
 * Copyright (C) 2017 Shobhit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.utilities.gdrive;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;

import org.odk.collect.android.R;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.utilities.ThemeUtils;

import java.util.Collections;

import javax.inject.Inject;

import static org.odk.collect.android.utilities.DialogUtils.showDialog;

public class GoogleAccountsManager {
    public static final int REQUEST_ACCOUNT_PICKER = 1000;
    public static final int REQUEST_AUTHORIZATION = 1001;

    @Nullable
    private Activity activity;
    @Nullable
    private HttpTransport transport;
    @Nullable
    private JsonFactory jsonFactory;
    @Nullable
    private GoogleAccountSelectionListener listener;

    private Intent intentChooseAccount;
    private Context context;
    private DriveHelper driveHelper;
    private SheetsHelper sheetsHelper;
    private GoogleAccountCredential credential;
    private GeneralSharedPreferences preferences;
    private ThemeUtils themeUtils;

    @Inject
    public GoogleAccountsManager(@NonNull Context context) {
        initCredential(context);
    }

    /**
     * This constructor should be used only for testing purposes
     */
    public GoogleAccountsManager(@NonNull GoogleAccountCredential credential,
                                 @NonNull GeneralSharedPreferences preferences,
                                 @NonNull Intent intentChooseAccount,
                                 @NonNull ThemeUtils themeUtils,
                                 @NonNull Activity activity,
                                 @NonNull Fragment fragment
    ) {
        this.credential = credential;
        this.preferences = preferences;
        this.intentChooseAccount = intentChooseAccount;
        this.themeUtils = themeUtils;
        this.activity = activity;
    }

    private void initCredential(@NonNull Context context) {
        this.context = context;

        transport = AndroidHttp.newCompatibleTransport();
        jsonFactory = JacksonFactory.getDefaultInstance();
        preferences = GeneralSharedPreferences.getInstance();

        credential = GoogleAccountCredential
                .usingOAuth2(context, Collections.singletonList(DriveScopes.DRIVE))
                .setBackOff(new ExponentialBackOff());

        intentChooseAccount = credential.newChooseAccountIntent();
        themeUtils = new ThemeUtils(context);
    }

    public void setSelectedAccountName(String accountName) {
        if (accountName != null) {
            preferences.save(GeneralKeys.KEY_SELECTED_GOOGLE_ACCOUNT, accountName);
            selectAccount(accountName);
        }
    }

    @NonNull
    public String getLastSelectedAccountIfValid() {
        Account[] googleAccounts = credential.getAllAccounts();
        String account = (String) preferences.get(GeneralKeys.KEY_SELECTED_GOOGLE_ACCOUNT);

        if (googleAccounts != null && googleAccounts.length > 0) {
            for (Account googleAccount : googleAccounts) {
                if (googleAccount.name.equals(account)) {
                    return account;
                }
            }

            preferences.reset(GeneralKeys.KEY_SELECTED_GOOGLE_ACCOUNT);
        }

        return "";
    }

    public static void showSettingsDialog(Activity activity) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.missing_google_account_dialog_title)
                .setMessage(R.string.missing_google_account_dialog_desc)
                .setOnCancelListener(dialog -> {
                    dialog.dismiss();
                    if (activity != null) {
                        activity.finish();
                    }
                })
                .setPositiveButton(activity.getString(R.string.ok), (dialog, which) -> {
                    dialog.dismiss();
                    activity.finish();
                })
                .create();

        showDialog(alertDialog, activity);
    }

    public void selectAccount(String accountName) {
        credential.setSelectedAccountName(accountName);
        if (listener != null) {
            listener.onGoogleAccountSelected(accountName);
        }
    }

    private Account getAccountPickerCurrentAccount() {
        String selectedAccountName = getLastSelectedAccountIfValid();
        if (selectedAccountName.isEmpty()) {
            Account[] googleAccounts = credential.getAllAccounts();
            if (googleAccounts != null && googleAccounts.length > 0) {
                selectedAccountName = googleAccounts[0].name;
            } else {
                return null;
            }
        }
        return new Account(selectedAccountName, "com.google");
    }

    public boolean isGoogleAccountSelected() {
        return credential.getSelectedAccountName() != null;
    }

    public DriveHelper getDriveHelper() {
        if (driveHelper == null && transport != null && jsonFactory != null) {
            driveHelper = new DriveHelper(credential, transport, jsonFactory);
        }
        return driveHelper;
    }

    public SheetsHelper getSheetsHelper() {
        if (sheetsHelper == null) {
            sheetsHelper = new SheetsHelper(credential, transport, jsonFactory);
        }
        return sheetsHelper;
    }

    @Nullable
    public Activity getActivity() {
        return activity;
    }

    @NonNull
    public Context getContext() {
        return context;
    }

    public GoogleAccountCredential getCredential() {
        return credential;
    }

    public void setListener(@Nullable GoogleAccountSelectionListener listener) {
        this.listener = listener;
    }

    public Intent getAccountChooserIntent() {
        Account selectedAccount = getAccountPickerCurrentAccount();
        intentChooseAccount.putExtra("selectedAccount", selectedAccount);
        intentChooseAccount.putExtra("overrideTheme", themeUtils.getAccountPickerTheme());
        intentChooseAccount.putExtra("overrideCustomTheme", 0);
        return intentChooseAccount;
    }

    public interface GoogleAccountSelectionListener {
        void onGoogleAccountSelected(String accountName);
    }
}

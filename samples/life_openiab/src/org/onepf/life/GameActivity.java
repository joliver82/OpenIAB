/*
 * Copyright 2012-2014 One Platform Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onepf.life;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.intellij.lang.annotations.MagicConstant;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.Purchase;

public class GameActivity extends Activity {
    public static final String TAG = "Life";
    private static final int REQUEST_CODE = 10001;

    //consumable
    private static final String SKU_CHANGES = "org.onepf.life3.changes";

    //non-consumable
    private static final String SKU_FIGURES = "org.onepf.life3.figures";

    //subscription
    private static final String SKU_ORANGE_CELLS = "org.onepf.life3.orange_cells";

    public static final String DEFAULT_PUBLIC_KEY = "YOUR_GOOGLE_PUBLIC_KEY";
    public static final String DEFAULT_APP_PACKAGE = "org.onepf.life";


    private LifeView lifeView;
    private ProgressDialog progressDialog;

    OpenIabHelper openIabHelper;

    final GameController gameController = new GameController();

    // Callback for when a purchase is finished
    private final IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener
            = new LifeGameOnIabPurchaseFinishedListener();

    // Called when consumption is complete
    private final IabHelper.OnConsumeFinishedListener consumeFinishedListener
            = new LifeGameOnConsumeFinishedListener();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUI();

        if (DEFAULT_APP_PACKAGE.equals(getPackageName())) {
            showErrorMessage(R.string.error_need_sign_app, false);
        }

        showProgressDialog(true);

        //public keys
        String googlePublicKey = DEFAULT_PUBLIC_KEY;
        String yandexPublicKey = DEFAULT_PUBLIC_KEY;
        //IAB helper
        OpenIabHelper.Options.Builder builder = new OpenIabHelper.Options.Builder();
        builder.addStoreKey(OpenIabHelper.NAME_GOOGLE, googlePublicKey);
        builder.addStoreKey(OpenIabHelper.NAME_YANDEX, yandexPublicKey);

        openIabHelper = new OpenIabHelper(this, builder.build());

        if (DEFAULT_PUBLIC_KEY.equals(googlePublicKey)
                || DEFAULT_PUBLIC_KEY.equals(yandexPublicKey)) {
            showErrorMessage(R.string.error_no_store_public_keys, true);
        } else {
            OpenIabHelper.Options options = builder.build();
            openIabHelper = new OpenIabHelper(this, options);
            openIabHelper.startSetup(new LifeGameOnIabSetupFinishedListener());
        }
    }

    private void showErrorMessage(@StringRes int messageResId, boolean finishActivityOnClose) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(messageResId)
                .setTitle(R.string.error)
                .setCancelable(false);
        if (finishActivityOnClose) {
            builder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    GameActivity.this.finish();
                }
            });
        } else {
            builder.setNeutralButton(R.string.cancel, null);
        }

        builder.create().show();
        Log.e(TAG, getString(messageResId));
    }

    private void initUI() {
        setContentView(R.layout.activity_game);
        lifeView = (LifeView) findViewById(R.id.life_view);
        lifeView.setUp(gameController, gameController.getChangeCount());
        //start/edit game button
        Button startButton = (Button) findViewById(R.id.start_button);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                lifeView.setEditMode(!lifeView.isInEditMode());
                ((Button) v).setText(
                        lifeView.isInEditMode() ? R.string.start_button : R.string.edit_button);
            }
        });
        startButton.setText(lifeView.isInEditMode() ? R.string.start_button : R.string.edit_button);
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return p != null;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        AppSettings appSettings = AppSettings.getInstance(this);
        boolean hasFigures = appSettings.isHasFigures();
        menu.setGroupVisible(R.id.menu_group_figures, hasFigures);
        menu.findItem(R.id.menu_buy_figures).setVisible(!hasFigures);
        menu.findItem(R.id.menu_buy_orange_cells).setVisible(appSettings.isHasOrangeCells());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_buy_changes:
                buyChanges();
                return true;

            case R.id.menu_buy_figures:
                buyFigures();
                return true;

            case R.id.menu_buy_orange_cells:
                buyOrangeCells();
                return true;

            case R.id.menu_empty_field:
                lifeView.drawFigure(null);
                return true;

            case R.id.menu_glider:
                lifeView.drawFigure(Figure.GLIDER);
                return true;

            case R.id.menu_big_glider:
                lifeView.drawFigure(Figure.BIG_GLIDER);
                return true;

            case R.id.menu_periodic:
                lifeView.drawFigure(Figure.PERIODIC);
                return true;

            case R.id.menu_robot:
                lifeView.drawFigure(Figure.ROBOT);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void buyChanges() {
        if (openIabHelper.getSetupState() == OpenIabHelper.SETUP_RESULT_SUCCESSFUL) {
            String payload = "";
            openIabHelper.launchPurchaseFlow(this, SKU_CHANGES,
                    REQUEST_CODE, purchaseFinishedListener, payload);
        } else {
            showOpenIABServiceSetupError(openIabHelper.getSetupState());
        }
    }

    void buyOrangeCells() {
        if (openIabHelper.getSetupState() == OpenIabHelper.SETUP_RESULT_SUCCESSFUL) {
            openIabHelper.launchSubscriptionPurchaseFlow(this, SKU_ORANGE_CELLS,
                    REQUEST_CODE, purchaseFinishedListener);
        } else {
            showOpenIABServiceSetupError(openIabHelper.getSetupState());
        }
    }

    void buyFigures() {
        if (openIabHelper.getSetupState() == OpenIabHelper.SETUP_RESULT_SUCCESSFUL) {
            String payload = "";
            openIabHelper.launchPurchaseFlow(this, SKU_FIGURES, REQUEST_CODE,
                    purchaseFinishedListener, payload);
        } else {
            showOpenIABServiceSetupError(openIabHelper.getSetupState());
        }
    }

    private void showOpenIABServiceSetupError(
            @MagicConstant(intValues = {OpenIabHelper.SETUP_DISPOSED,
                    OpenIabHelper.SETUP_IN_PROGRESS,
                    OpenIabHelper.SETUP_RESULT_NOT_STARTED,
                    OpenIabHelper.SETUP_RESULT_FAILED})
            int setupState) {

        switch (setupState) {
            case OpenIabHelper.SETUP_IN_PROGRESS:
                showErrorMessage(R.string.error_openiab_setup_in_progress, false);
                break;

            case OpenIabHelper.SETUP_RESULT_FAILED:
                showErrorMessage(R.string.error_openiab_setup_result_failure, false);
                break;

            case OpenIabHelper.SETUP_RESULT_NOT_STARTED:
                showErrorMessage(R.string.error_openiab_setup_not_started, false);
                break;

            case OpenIabHelper.SETUP_DISPOSED:
                showErrorMessage(R.string.error_openiab_setup_disposed, false);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        // Pass on the activity result to the helper for handling
        if (openIabHelper.handleActivityResult(requestCode, resultCode, data)) {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        } else {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    void showProgressDialog(boolean show) {
        if (show) {
            if (progressDialog != null && !progressDialog.isShowing()) {
                progressDialog = ProgressDialog.show(this, null, getString(R.string.waiting_dialog_message));
            }
        } else if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    /**
     * Created by krozov on 7/28/14.
     */
    public final class GameController implements LifeView.Listener {
        private int changesCount = -1;

        public GameController() {
            changesCount = AppSettings.getInstance(GameActivity.this).getChangesCount();
        }

        @Override
        public void onChangeCountModified(int newChangeCount) {
            AppSettings.getInstance(GameActivity.this).setChangesCount(newChangeCount);
            changesCount = newChangeCount;
        }

        @Override
        public void onNoChangesFound() {
            new AlertDialog.Builder(GameActivity.this)
                    .setTitle(R.string.changes_ended)
                    .setMessage(R.string.changes_ended_message)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            buyChanges();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();
        }

        @Override
        public void onNotEnoughSpaceForFigure(Figure figure) {
            Toast.makeText(GameActivity.this, R.string.not_enough_space, Toast.LENGTH_SHORT).show();
        }

        private void increaseChangesCount(int additionChangesCount) {
            if (additionChangesCount < 0) {
                throw new IllegalArgumentException("Can't add '" + additionChangesCount + "'" +
                        " changes count. Negative value impossible");
            }

            if (additionChangesCount != 0) {
                changesCount += additionChangesCount;
                onChangeCountModified(changesCount);
                lifeView.setChangeCount(changesCount);
            }
        }

        private int getChangeCount() {
            if (changesCount == -1) {
                changesCount = AppSettings.getInstance(GameActivity.this).getChangesCount();
            }
            return changesCount;
        }
    }

    private class LifeGameQueryInventoryFinishedListener implements IabHelper.QueryInventoryFinishedListener {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isFailure()) {
                Toast.makeText(GameActivity.this, "Failed to query inventory: " + result, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(GameActivity.this, "Query inventory was successful." + result, Toast.LENGTH_SHORT).show();
            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            // check for orange cells subscription
            Purchase orangeCellsPurchase = inventory.getPurchase(SKU_ORANGE_CELLS);
            if (verifyDeveloperPayload(orangeCellsPurchase)) {
                AppSettings.getInstance(GameActivity.this).setHasOrangeCells(true);
                lifeView.setActiveCellBitmap(R.drawable.cell_active_orange);
            }

            //non-consumable figures
            Purchase figuresPurchase = inventory.getPurchase(SKU_FIGURES);
            if (verifyDeveloperPayload(figuresPurchase)) {
                AppSettings.getInstance(GameActivity.this).setHasFigures(true);
            }

            //consumable changes
            Purchase changesPurchase = inventory.getPurchase(SKU_CHANGES);
            if (verifyDeveloperPayload(changesPurchase)) {
                openIabHelper.consumeAsync(changesPurchase, consumeFinishedListener);
            }

            invalidateOptionsMenu();
        }
    }

    private class LifeGameOnIabPurchaseFinishedListener implements IabHelper.OnIabPurchaseFinishedListener {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);
            if (result.isFailure()) {
                Toast.makeText(GameActivity.this, "Error purchasing: " + result,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                Toast.makeText(GameActivity.this, "Error purchasing. Authenticity" +
                        " verification failed.", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (SKU_CHANGES.equals(purchase.getSku())) {
                openIabHelper.consumeAsync(purchase, consumeFinishedListener);
            } else {
                if (SKU_FIGURES.equals(purchase.getSku())) {
                    AppSettings.getInstance(GameActivity.this).setHasFigures(true);
                } else if (SKU_ORANGE_CELLS.equals(purchase.getSku())) {
                    Toast.makeText(GameActivity.this, R.string.subscribe_thank, Toast.LENGTH_SHORT).show();
                    AppSettings.getInstance(GameActivity.this).setHasOrangeCells(true);
                    lifeView.setActiveCellBitmap(R.drawable.cell_active_orange);
                }
                invalidateOptionsMenu();
            }
        }
    }

    private class LifeGameOnConsumeFinishedListener implements IabHelper.OnConsumeFinishedListener {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);
            if (result.isSuccess()) {
                if (SKU_CHANGES.equals(purchase.getSku())) {
                    gameController.increaseChangesCount(LifeView.DEFAULT_CHANGES_COUNT);
                }
            } else {
                Toast.makeText(GameActivity.this, "unsuccessful consume", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class LifeGameOnIabSetupFinishedListener implements IabHelper.OnIabSetupFinishedListener {
        public void onIabSetupFinished(IabResult result) {
            Log.d(TAG, "Setup finished.");
            showProgressDialog(false);
            if (result.isSuccess()) {
                Log.d(TAG, "Setup successful. Queuing inventory.");
                openIabHelper.queryInventoryAsync(new LifeGameQueryInventoryFinishedListener());
            } else {
                Toast.makeText(GameActivity.this, "Problem setting up in-app billing: "
                        + result, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
/*
 * Copyright 2018 Idealnaya rabota LLC
 * Licensed under Multy.io license.
 * See LICENSE for details
 */

package io.multy.util;

import android.util.Log;

import com.google.gson.Gson;
import com.samwolfand.oneprefs.Prefs;

import java.math.BigDecimal;

import io.multy.Multy;
import io.multy.R;
import io.multy.model.core.Account;
import io.multy.model.core.Builder;
import io.multy.model.core.Payload;
import io.multy.model.core.Transaction;
import io.multy.model.core.TransactionBuilder;
import io.multy.model.core.TransactionResponse;
import io.multy.model.entities.TransactionBTCMeta;
import io.multy.model.entities.TransactionERC20Meta;
import io.multy.model.entities.TransactionETHMeta;
import io.multy.model.entities.wallet.EthWallet;
import io.multy.model.entities.wallet.Wallet;
import io.multy.model.entities.wallet.WalletPrivateKey;
import io.multy.storage.RealmManager;

public class TransactionHelper {
    public static final String TAG = TransactionHelper.class.getSimpleName();
    private static volatile TransactionHelper instance = new TransactionHelper();

    private TransactionHelper(){
        if (instance != null){
            throw new RuntimeException("Use getInstance to call this class");
        }
    }

    public synchronized static TransactionHelper getInstance(){

        if (instance == null){
            instance = new TransactionHelper();
        }

        return instance;
    }


    /**
     *
     * @param fromWallet
     * @param amount amount should be BTC
     * @param feeRate
     * @param toAddress
     * @param donationAmount
     * @param donationAddress
     * @param payingForComission
     * @return
     */

    public static String createBTCTransaction(Wallet fromWallet, String amount, String feeRate, String toAddress, String donationAmount, String donationAddress, boolean payingForComission ){
        Log.d(TAG, "AMOUNT IS :"+amount);
//        2019-02-20 12:47:33.184 1642-1642/io.multy.debug D/TransactionHelper: AMOUNT IS :0.00007802
//        2019-02-20 12:47:33.367 1642-1642/io.multy.debug D/TransactionHelper: SIGN AMOUNT IS :7802

//        2019-02-20 13:34:48.108 5524-5524/io.multy.debug D/TransactionHelper: AMOUNT IS :0.00130036
//        2019-02-20 13:34:48.121 5524-5524/io.multy.debug D/TransactionHelper: SIGN AMOUNT IS :130036
        TransactionBTCMeta meta = buildBTCTransactionMeta(fromWallet, feeRate, amount, toAddress, donationAmount, donationAddress, payingForComission);
        return signBTCTransaction(meta);
    }

    /**
     *
     * @param fromWallet
     * @param feeRate
     * @param gasLimit
     * @param amount    amount should be in ETH not WEI
     * @param toAddress
     * @param payingForComission
     * @return
     */

    public static String createETHTransaction(Wallet fromWallet, String feeRate, String gasLimit, String amount, String toAddress, boolean payingForComission){
        Log.d(TAG, "AMOUNT IS :"+amount);
        TransactionETHMeta meta = buildETHTransactionMeta(fromWallet, feeRate, gasLimit, amount, toAddress, payingForComission);
        return signETHTransaction(meta);
    }

    public static String makeTransaction(Object metaTX){
        if (metaTX instanceof TransactionBTCMeta){
            return signBTCTransaction((TransactionBTCMeta) metaTX);
        } else if (metaTX instanceof TransactionETHMeta){
            return signETHTransaction((TransactionETHMeta) metaTX);
        } else if (metaTX instanceof TransactionERC20Meta) {
            return signERC20Transaction((TransactionERC20Meta) metaTX);
        } else {
            //THIS is not supported blockchains yet
            return null;
        }

    }

    public static String estimateBTCTransactionByRawData(Wallet fromWallet, String amount, String feeRate, String toAddress, String donationAmount, String donationAddress, boolean payingForComission ){
        TransactionBTCMeta meta = buildBTCTransactionMeta(fromWallet, feeRate, amount, toAddress, donationAmount, donationAddress, payingForComission);
        return estimateTransactionFee(meta);
    }

    public static String estimateTransactionFee(Object metaTX){
        if (metaTX instanceof TransactionBTCMeta){
           return estimateBTCTransaction((TransactionBTCMeta) metaTX);


        } else if (metaTX instanceof TransactionETHMeta){
            //TODO return Estimate ETH TRansaction
            return null;
        } else {
            //THIS is not supported blockchains yet
            return null;
        }
    }


    private static String estimateBTCTransaction(TransactionBTCMeta meta){
        byte[] seed = RealmManager.getSettingsDao().getSeed().getSeed();
        try {
            return NativeDataHelper.estimateTransactionFee(meta.getWallet().getId(), meta.getWallet().getNetworkId(),
                    seed, meta.getWallet().getIndex(), String.valueOf(CryptoFormatUtils.btcToSatoshi(String.valueOf(String.valueOf(meta.getAmount())))),
                    meta.getFeeRate(), getDonationSatoshi(meta.getDonationAmount()),
                    meta.getToAddress(), meta.getChangeAddress(), meta.getDonationAddress(), meta.isPayingForComission());
        } catch (JniException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] buildBTCTransaction(TransactionBTCMeta meta){
        byte[] seed = RealmManager.getSettingsDao().getSeed().getSeed();
        String signAmount = String.valueOf(CryptoFormatUtils.btcToSatoshi(String.valueOf(meta.getAmount())));
        Log.d(TAG, "SIGN AMOUNT IS :"+signAmount);
        try {
            byte[] transactionHex = NativeDataHelper.makeTransaction(meta.getWallet().getId(), meta.getWallet().getNetworkId(),
                    seed, meta.getWallet().getIndex(), signAmount,
                    meta.getFeeRate(), getDonationSatoshi(meta.getDonationAmount()),
                    meta.getToAddress(), meta.getChangeAddress(), meta.getDonationAddress(), meta.isPayingForComission());
//            return byteArrayToHex(transactionHex);

            return transactionHex;
        } catch (JniException e) {
//            errorMessage.setValue(Multy.getContext().getString(R.string.invalid_entered_sum));
            e.printStackTrace();
        }
        return null;
    }

    private static String signBTCTransaction(TransactionBTCMeta meta) {
        return byteArrayToHex(buildBTCTransaction(meta));
    }


    private static String signERC20Transaction(TransactionERC20Meta metaTX){
        Wallet wallet = metaTX.getWallet();
//        BigDecimal amount = new BigDecimal(metaTX.getAmount());
//        BigDecimal balance = new BigDecimal(metaTX.getTokenBalance());
//        viewModel.tokensAmount.setValue(inputOriginal.getText().toString());

        Payload payload = new Payload(wallet.getActiveAddress().getAmountString(),
                metaTX.getContractAddress(),                    //Smart Contract Address
                metaTX.getTokenBalance(),                       //Balance of all the tokens
                metaTX.getAmount(),                             //Amount to send
                metaTX.getToAddress());                         //Send To Address

        Builder builder = new Builder(Builder.TYPE_ERC20, Builder.ACTION_TRANSFER, payload);

        Transaction transaction = new Transaction(wallet.getEthWallet().getNonce(), new io.multy.model.core.Fee(
                String.valueOf(metaTX.getFeeRate()), Constants.GAS_LIMIT_TOKEN_TANSFER));

        final int walletIndex = Prefs.getBoolean(Constants.PREF_METAMASK_MODE, false) ? wallet.getActiveAddress().getIndex() : wallet.getIndex();
        final int addressIndex = Prefs.getBoolean(Constants.PREF_METAMASK_MODE, false) ? wallet.getIndex() : wallet.getActiveAddress().getIndex();



        TransactionBuilder transactionBuilder = new TransactionBuilder(
                NativeDataHelper.Blockchain.ETH.getName(),
                NativeDataHelper.NetworkId.ETH_MAIN_NET.getValue(),
                Account.getAccount(walletIndex, addressIndex, wallet.getNetworkId()),
                builder,
                transaction);

        try {
            String json = NativeDataHelper.makeTransactionJSONAPI(new Gson().toJson(transactionBuilder));
            TransactionResponse transactionResponse = new Gson().fromJson(json, TransactionResponse.class);
            if (transactionResponse == null) {
                return null;
            } else {
                return transactionResponse.getTransactionHex();
            }
        } catch (JniException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String signETHTransaction(TransactionETHMeta metaTX) {
        try {
            String signAmount;
            double amountInEth = Double.parseDouble(metaTX.getAmount());
            double feeRateInEth = EthWallet.getTransactionPrice(Long.parseLong(metaTX.getFeeRate()));
            double cleanAmount = amountInEth - feeRateInEth;

            if (metaTX.getWallet().isMultisig()) {
                double multiFeeRateInEth = EthWallet.getTransactionMultisigPrice(Long.parseLong(metaTX.getFeeRate()), Long.parseLong(metaTX.getGasLimit()));
                cleanAmount = amountInEth - multiFeeRateInEth;
                signAmount = CryptoFormatUtils.ethToWei(String.valueOf(metaTX.isPayingForComission() ?
//                        metaTX.getAmount() : (metaTX.getAmount() - EthWallet.getTransactionMultisigPrice(metaTX.getFeeRate(), Long.parseLong(estimation.getValue().getSubmitTransaction())))));
                        metaTX.getAmount() : String.valueOf(cleanAmount)));
            } else {
                signAmount = CryptoFormatUtils.ethToWei(String.valueOf(metaTX.isPayingForComission() ?
                        metaTX.getAmount() : String.valueOf(cleanAmount)));
            }
            Log.d(TAG, "SIGN AMOUNT IS:"+signAmount);
            byte[] tx;
            TransactionResponse txResult = null;
            if (metaTX.getWallet().isMultisig()) {
                Wallet linkedWallet = RealmManager.getAssetsDao().getMultisigLinkedWallet(metaTX.getWallet().getMultisigWallet().getOwners());
                double price = EthWallet.getTransactionMultisigPrice(Long.parseLong(metaTX.getFeeRate()), Long.parseLong(metaTX.getGasLimit()));
                if (linkedWallet.getAvailableBalanceNumeric().compareTo(new BigDecimal(CryptoFormatUtils.ethToWei(price))) < 0) {
//                    errorMessage.setValue(Multy.getContext().getString(R.string.not_enough_linked_balance));
                    return null;
                }
                if (linkedWallet.shouldUseExternalKey()) {
                    WalletPrivateKey privateKey = RealmManager.getAssetsDao()
                            .getPrivateKey(linkedWallet.getActiveAddress().getAddress(), linkedWallet.getCurrencyId(), linkedWallet.getNetworkId());
                    tx = NativeDataHelper.makeTransactionMultisigETHFromKey(
                            privateKey.getPrivateKey(),
                            linkedWallet.getCurrencyId(),
                            linkedWallet.getNetworkId(),
                            linkedWallet.getActiveAddress().getAmountString(),
                            metaTX.getWallet().getActiveAddress().getAddress(),
                            signAmount,
                            metaTX.getToAddress(),
                            //TODO please test this solution!
//                            estimation.getValue().getSubmitTransaction(),
                            String.valueOf(metaTX.getGasLimit()),
                            String.valueOf(metaTX.getFeeRate()),
                            linkedWallet.getEthWallet().getNonce());
                } else {
                    tx = NativeDataHelper.makeTransactionMultisigETH(
                            RealmManager.getSettingsDao().getSeed().getSeed(),
                            linkedWallet.getIndex(),
                            0,
                            linkedWallet.getCurrencyId(),
                            linkedWallet.getNetworkId(),
                            linkedWallet.getActiveAddress().getAmountString(),
                            metaTX.getWallet().getActiveAddress().getAddress(),
                            signAmount,
                            metaTX.getToAddress(),
                            //TODO please test this solution!
//                            estimation.getValue().getSubmitTransaction(),
                            metaTX.getGasLimit(),
                            metaTX.getFeeRate(),
                            linkedWallet.getEthWallet().getNonce());
                }
            } else if (metaTX.getWallet().shouldUseExternalKey()) {
                WalletPrivateKey keyObject = RealmManager.getAssetsDao()
                        .getPrivateKey(metaTX.getWallet().getActiveAddress().getAddress(), metaTX.getWallet().getCurrencyId(), metaTX.getWallet().getNetworkId());
                tx = NativeDataHelper.makeTransactionETHFromKey(
                        keyObject.getPrivateKey(),
                        metaTX.getWallet().getCurrencyId(),
                        metaTX.getWallet().getNetworkId(),
                        metaTX.getWallet().getActiveAddress().getAmountString(),
                        signAmount, metaTX.getToAddress(),
                        //TODO please test this solution
//                        gasLimit.getValue(),
                        metaTX.getGasLimit(),
                        metaTX.getFeeRate(),
                        metaTX.getWallet().getEthWallet().getNonce());
            } else {
                //now see some true magic. lost two days for that sh8t
                final int walletIndex = Prefs.getBoolean(Constants.PREF_METAMASK_MODE, false) ? metaTX.getWallet().getActiveAddress().getIndex() : metaTX.getWallet().getIndex();
                final int addressIndex = Prefs.getBoolean(Constants.PREF_METAMASK_MODE, false) ? metaTX.getWallet().getIndex() : metaTX.getWallet().getActiveAddress().getIndex();

                final String key = NativeDataHelper.getMyPrivateKey(RealmManager.getSettingsDao().getSeed().getSeed(), walletIndex, addressIndex,
                        NativeDataHelper.Blockchain.ETH.getValue(), Prefs.getBoolean(Constants.PREF_METAMASK_MODE, false) ? NativeDataHelper.NetworkId.ETH_MAIN_NET.getValue() : metaTX.getWallet().getNetworkId());

//                final String key2 = NativeDataHelper.getMyPrivateKey(RealmManager.getSettingsDao().getSeed().getSeed(), wallet.getIndex(),
//                        wallet.getNetworkId(), NativeDataHelper.Blockchain.ETH.getValue(), wallet.getValue().getNetworkId());

                TransactionBuilder builder =
                        new TransactionBuilder(
                                NativeDataHelper.Blockchain.ETH.getName(),
                                metaTX.getWallet().getNetworkId(),
                                new Account(Account.ACCOUNT_TYPE_DEFAULT, key),
                                new Builder(Builder.TYPE_BASIC,
                                        new Payload(metaTX.getWallet().getActiveAddress().getAmountString(),
                                                metaTX.getToAddress(), signAmount)), new Transaction(metaTX.getWallet().getEthWallet().getNonce(),
                                new io.multy.model.core.Fee(metaTX.getFeeRate(), metaTX.getGasLimit())
                        ));

                String json = new Gson().toJson(builder);
                tx = null;
                txResult = new Gson().fromJson(NativeDataHelper.makeTransactionJSONAPI(json), TransactionResponse.class);


            }

            return tx == null ? txResult.getTransactionHex() : byteArrayToHex(tx);
        } catch (JniException e) {
//            errorMessage.setValue(Multy.getContext().getString(R.string.invalid_entered_sum));
            e.printStackTrace();
        }
        return null;
    }

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String getDonationSatoshi(String amount) {
        if (amount != null) {
            return String.valueOf((long) (Double.valueOf(amount) * Math.pow(10, 8)));
        } else {
            return "0";
        }
    }


    private static TransactionBTCMeta buildBTCTransactionMeta(Wallet fromWallet, String feeRate, String amount, String payToAddress, String donationAmount, String donationAddress, boolean payingForComission){

        String changeAddress = getChangeAddress(fromWallet);


        TransactionBTCMeta meta = new TransactionBTCMeta(
                fromWallet,                                       //From Wallet
                feeRate,                //Fee rate
                payToAddress,                                                   //To Address
                changeAddress                                                   //Change Address
        );

        meta.setAmount(amount);
        meta.setDonationAmount(donationAmount);
        meta.setDonationAddress(donationAddress); //this is just fake data ..Core ignore this field
        meta.setPayingForComission(payingForComission);                                      //Should check if sending all amount of the wallet or not

        return meta;
    }

    private static TransactionETHMeta buildETHTransactionMeta(Wallet wallet, String feeRate, String gasLimit, String amount, String payToAddress, boolean payingForComission){
        TransactionETHMeta meta = new TransactionETHMeta(
                wallet,                  //Wallet to pay from
                feeRate,
                gasLimit,
                payToAddress
        );
        meta.setAmount(amount);
        meta.setPayingForComission(payingForComission);

        return meta;
    }

    public static String getChangeAddress(Wallet wallet){
        byte[] seed = RealmManager.getSettingsDao().getSeed().getSeed();
        String changeAddress = null;
        try {
            changeAddress = NativeDataHelper.makeAccountAddress(seed, wallet.getIndex(), wallet.getBtcWallet().getAddresses().size(),
                    wallet.getCurrencyId(), wallet.getNetworkId());
        } catch (JniException e) {
            e.printStackTrace();
        }
        return changeAddress;
    }
}

package com.polidea.rxandroidble.internal.operations;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.RxBleRadioOperation;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;

import java.util.List;

public class RxBleRadioOperationScan21 extends RxBleRadioOperation<ScanResult> {

    private final List<ScanFilter> filters;
    private final ScanSettings scanSettings;
    private final RxBleAdapterWrapper rxBleAdapterWrapper;

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            onNext(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                onNext(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            onError(new BleScanException(errorCode));
        }
    };

    public RxBleRadioOperationScan21(List<ScanFilter> filters, ScanSettings scanSettings,
            RxBleAdapterWrapper rxBleAdapterWrapper) {

        this.filters = filters;
        this.scanSettings = scanSettings;
        this.rxBleAdapterWrapper = rxBleAdapterWrapper;
    }

    @Override
    public void run() {
        if (scanSettings == null) {
            rxBleAdapterWrapper.startScan(filters, new ScanSettings.Builder().build(), scanCallback);
        } else {
            rxBleAdapterWrapper.startScan(filters, scanSettings, scanCallback);
        }
        releaseRadio();
    }

    public void stop() {
        rxBleAdapterWrapper.stopScan(scanCallback);
    }

}

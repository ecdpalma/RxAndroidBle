package com.polidea.rxandroidble.internal.operations;

import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.RxBleRadioOperation;

import java.util.List;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class RxBleRadioOperationScanCompat extends RxBleRadioOperation<ScanResult> {

    private final List<ScanFilter> filters;
    private final ScanSettings scanSettings;
    private final BluetoothLeScannerCompat scanner;

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

    public RxBleRadioOperationScanCompat(List<ScanFilter> filters, ScanSettings scanSettings,
            BluetoothLeScannerCompat scanner) {

        this.filters = filters;
        this.scanSettings = scanSettings;
        this.scanner = scanner;
    }

    @Override
    public void run() {
        if (scanSettings == null) {
            scanner.startScan(filters, new ScanSettings.Builder().build(), scanCallback);
        } else if (filters == null) {
            scanner.startScan(null, scanSettings, scanCallback);
        } else {
            scanner.startScan(filters, scanSettings, scanCallback);
        }
        releaseRadio();
    }

    public void stop() {
        scanner.stopScan(scanCallback);
    }
}

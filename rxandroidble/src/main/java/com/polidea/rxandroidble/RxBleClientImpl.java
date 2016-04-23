package com.polidea.rxandroidble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.polidea.rxandroidble.RxBleAdapterStateObservable.BleAdapterState;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.RxBleDeviceProvider;
import com.polidea.rxandroidble.internal.RxBleInternalScanResult;
import com.polidea.rxandroidble.internal.RxBleRadio;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationScan;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationScan21;
import com.polidea.rxandroidble.internal.radio.RxBleRadioImpl;
import com.polidea.rxandroidble.internal.util.BleConnectionCompat;
import com.polidea.rxandroidble.internal.util.LocationServicesStatus;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import com.polidea.rxandroidble.internal.util.UUIDUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rx.Observable;

class RxBleClientImpl extends RxBleClient {

    private final RxBleRadio rxBleRadio;
    private final UUIDUtil uuidUtil;
    private final RxBleDeviceProvider rxBleDeviceProvider;
    private final Map<Set<UUID>, Observable<RxBleScanResult>> queuedScanOperations = new HashMap<>();
    private final Map<List<ScanFilter>, Observable<ScanResult>> queuedScanOperations21 = new HashMap<>();

    private final RxBleAdapterWrapper rxBleAdapterWrapper;
    private final Observable<BleAdapterState> rxBleAdapterStateObservable;
    private final LocationServicesStatus locationServicesStatus;

    RxBleClientImpl(RxBleAdapterWrapper rxBleAdapterWrapper,
                    RxBleRadio rxBleRadio,
                    Observable<BleAdapterState> adapterStateObservable,
                    UUIDUtil uuidUtil,
                    BleConnectionCompat bleConnectionCompat,
                    LocationServicesStatus locationServicesStatus) {
        this.uuidUtil = uuidUtil;
        this.rxBleRadio = rxBleRadio;
        this.rxBleAdapterWrapper = rxBleAdapterWrapper;
        this.rxBleAdapterStateObservable = adapterStateObservable;
        this.locationServicesStatus = locationServicesStatus;
        rxBleDeviceProvider = new RxBleDeviceProvider(this.rxBleAdapterWrapper, this.rxBleRadio, bleConnectionCompat);
    }

    public static RxBleClientImpl getInstance(@NonNull Context context) {
        return new RxBleClientImpl(
                new RxBleAdapterWrapper(BluetoothAdapter.getDefaultAdapter()),
                new RxBleRadioImpl(),
                new RxBleAdapterStateObservable(context.getApplicationContext()),
                new UUIDUtil(),
                new BleConnectionCompat(context),
                new LocationServicesStatus(context, (LocationManager) context.getSystemService(Context.LOCATION_SERVICE)));
    }

    @Override
    public RxBleDevice getBleDevice(@NonNull String macAddress) {
        return rxBleDeviceProvider.getBleDevice(macAddress);
    }

    @Override
    public Observable<RxBleScanResult> scanBleDevices(@Nullable UUID... filterServiceUUIDs) {

        if (!rxBleAdapterWrapper.hasBluetoothAdapter()) {
            return Observable.error(new BleScanException(BleScanException.BLUETOOTH_NOT_AVAILABLE));
        } else if (!rxBleAdapterWrapper.isBluetoothEnabled()) {
            return Observable.error(new BleScanException(BleScanException.BLUETOOTH_DISABLED));
        } else if (checkIfLocationPermissionIsGrantedIfRequired()) {
            return Observable.error(new BleScanException(BleScanException.LOCATION_PERMISSION_MISSING));
        } else if (checkIfLocationAccessIsEnabledIfRequired()) {
            return Observable.error(new BleScanException(BleScanException.LOCATION_SERVICES_DISABLED));
        } else {
            return initializeScan(filterServiceUUIDs);
        }
    }

    @Override
    public Observable<ScanResult> scanBleDevices(List<ScanFilter> filters, ScanSettings settings) {

        if (!rxBleAdapterWrapper.hasBluetoothAdapter()) {
            return Observable.error(new BleScanException(BleScanException.BLUETOOTH_NOT_AVAILABLE));
        } else if (!rxBleAdapterWrapper.isBluetoothEnabled()) {
            return Observable.error(new BleScanException(BleScanException.BLUETOOTH_DISABLED));
        } else if (checkIfLocationPermissionIsGrantedIfRequired()) {
            return Observable.error(new BleScanException(BleScanException.LOCATION_PERMISSION_MISSING));
        } else if (checkIfLocationAccessIsEnabledIfRequired()) {
            return Observable.error(new BleScanException(BleScanException.LOCATION_SERVICES_DISABLED));
        } else {
            return initializeScan(filters, settings);
        }
    }

    private Observable<ScanResult> initializeScan(List<ScanFilter> filters, ScanSettings settings) {
        synchronized (queuedScanOperations21) {
            Observable<ScanResult> matchingQueuedScan = queuedScanOperations21.get(filters);

            if (matchingQueuedScan == null) {
                matchingQueuedScan = createScanOperation(filters, settings);
                queuedScanOperations21.put(filters, matchingQueuedScan);
            }

            return matchingQueuedScan;
        }
    }

    private Observable<RxBleScanResult> initializeScan(@Nullable UUID[] filterServiceUUIDs) {
        final Set<UUID> filteredUUIDs = uuidUtil.toDistinctSet(filterServiceUUIDs);

        synchronized (queuedScanOperations) {
            Observable<RxBleScanResult> matchingQueuedScan = queuedScanOperations.get(filteredUUIDs);

            if (matchingQueuedScan == null) {
                matchingQueuedScan = createScanOperation(filterServiceUUIDs);
                queuedScanOperations.put(filteredUUIDs, matchingQueuedScan);
            }

            return matchingQueuedScan;
        }
    }

    private boolean checkIfLocationAccessIsEnabledIfRequired() {
        return locationServicesStatus.isLocationProviderRequired() && !locationServicesStatus.isLocationProviderEnabled();
    }

    private boolean checkIfLocationPermissionIsGrantedIfRequired() {
        return locationServicesStatus.isLocationProviderRequired() && !locationServicesStatus.isLocationPermissionApproved();
    }

    private <T> Observable<T> bluetoothAdapterOffExceptionObservable() {
        return rxBleAdapterStateObservable
                .filter(state -> state != BleAdapterState.STATE_ON)
                .first()
                .flatMap(status -> Observable.error(new BleScanException(BleScanException.BLUETOOTH_DISABLED)));
    }

    private RxBleScanResult convertToPublicScanResult(RxBleInternalScanResult scanResult) {
        final BluetoothDevice bluetoothDevice = scanResult.getBluetoothDevice();
        final RxBleDevice bleDevice = getBleDevice(bluetoothDevice.getAddress());
        return new RxBleScanResult(bleDevice, scanResult.getRssi(), scanResult.getScanRecord());
    }

    private Observable<RxBleScanResult> createScanOperation(@Nullable UUID[] filterServiceUUIDs) {
        final Set<UUID> filteredUUIDs = uuidUtil.toDistinctSet(filterServiceUUIDs);
        final RxBleRadioOperationScan scanOperation = new RxBleRadioOperationScan(filterServiceUUIDs, rxBleAdapterWrapper, uuidUtil);
        return rxBleRadio.queue(scanOperation)
                .doOnUnsubscribe(() -> {

                    synchronized (queuedScanOperations) {
                        scanOperation.stop();
                        queuedScanOperations.remove(filteredUUIDs);
                    }
                })
                .mergeWith(bluetoothAdapterOffExceptionObservable())
                .map(this::convertToPublicScanResult)
                .share();
    }

    private Observable<ScanResult> createScanOperation(List<ScanFilter> filters, ScanSettings settings) {
        final RxBleRadioOperationScan21 scanOperation =
                new RxBleRadioOperationScan21(filters, settings, rxBleAdapterWrapper);
        return rxBleRadio.queue(scanOperation)
                .doOnUnsubscribe(() -> {
                    synchronized (queuedScanOperations21) {
                        scanOperation.stop();
                        queuedScanOperations21.remove(filters);
                    }
                })
                .mergeWith(bluetoothAdapterOffExceptionObservable())
                .share();
    }
}

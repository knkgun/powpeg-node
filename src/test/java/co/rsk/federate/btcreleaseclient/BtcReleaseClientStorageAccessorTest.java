package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileData;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileReadResult;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorage;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bitcoinj.core.NetworkParameters;
import org.ethereum.config.Constants;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

public class BtcReleaseClientStorageAccessorTest {

    @Test(expected = InvalidStorageFileException.class)
    public void invalid_file() throws IOException, InvalidStorageFileException {
        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(false, null));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                getImmediateTaskExecutor(),
                btcReleaseClientFileStorage,
                10,
                1
            );

        new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());
    }

    @Test
    public void works_with_no_file() throws InvalidStorageFileException, IOException {
        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, new BtcReleaseClientFileData()));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                getImmediateTaskExecutor(),
                btcReleaseClientFileStorage,
                10,
                1
            );

        assertFalse(storageAccessor.getBestBlockHash().isPresent());
        assertEquals(0, storageAccessor.getMapSize());
    }

    @Test
    public void getBestBlockHash_ok() throws IOException, InvalidStorageFileException {
        Keccak256 bestBlockHash = createHash(1);

        BtcReleaseClientFileData btcReleaseClientFileData = new BtcReleaseClientFileData();
        btcReleaseClientFileData.setBestBlockHash(bestBlockHash);

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, btcReleaseClientFileData));

        BtcReleaseClientStorageAccessor storageAccessor = new BtcReleaseClientStorageAccessor(
            getFedNodeSystemProperties(),
            getImmediateTaskExecutor(),
            btcReleaseClientFileStorage,
            0,
            0
        );

        assertTrue(storageAccessor.getBestBlockHash().isPresent());
        assertEquals(bestBlockHash, storageAccessor.getBestBlockHash().get());
    }

    @Test
    public void setBestBlockHash_ok() throws InvalidStorageFileException, IOException {
        Keccak256 bestBlockHash = createHash(1);

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, new BtcReleaseClientFileData()));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                getImmediateTaskExecutor(),
                btcReleaseClientFileStorage,
                10,
                1
            );

        storageAccessor.setBestBlockHash(bestBlockHash);

        assertTrue(storageAccessor.getBestBlockHash().isPresent());
        assertEquals(bestBlockHash, storageAccessor.getBestBlockHash().get());
    }

    @Test
    public void getRskTxHash_and_hasBtcTxHash_ok() throws IOException, InvalidStorageFileException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);

        BtcReleaseClientFileData btcReleaseClientFileData = new BtcReleaseClientFileData();
        btcReleaseClientFileData.getReleaseHashesMap().put(btcTxHash, rskTxHash);

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, btcReleaseClientFileData));

        BtcReleaseClientStorageAccessor storageAccessor = new BtcReleaseClientStorageAccessor(
            getFedNodeSystemProperties(),
            getImmediateTaskExecutor(),
            btcReleaseClientFileStorage,
            0,
            0
        );

        assertEquals(1, storageAccessor.getMapSize());
        assertTrue(storageAccessor.hasBtcTxHash(btcTxHash));
        assertEquals(rskTxHash, storageAccessor.getRskTxHash(btcTxHash));
    }

    @Test
    public void putBtcTxHashRskTxHash_ok() throws InvalidStorageFileException, IOException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);

        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        // Ignore delay and execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)a.getArgument(0)).run();
            return null;
        }).when(executorService).schedule(any(Runnable.class), anyLong(), any());

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, new BtcReleaseClientFileData()));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                executorService,
                btcReleaseClientFileStorage,
                10,
                1
            );

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);

        verify(btcReleaseClientFileStorage, times(1)).write(any(BtcReleaseClientFileData.class));

        assertEquals(1, storageAccessor.getMapSize());
        assertTrue(storageAccessor.hasBtcTxHash(btcTxHash));
        assertEquals(rskTxHash, storageAccessor.getRskTxHash(btcTxHash));
    }

    @Test
    public void writes_to_file()
        throws InvalidStorageFileException, IOException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);
        Keccak256 bestBlockHash = createHash(2);

        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        // Ignore delay and execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)a.getArgument(0)).run();
            return null;
        }).when(executorService).schedule(any(Runnable.class), anyLong(), any());

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, new BtcReleaseClientFileData()));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                executorService,
                btcReleaseClientFileStorage,
                10,
                2
            );

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);
        storageAccessor.setBestBlockHash(bestBlockHash);

        // As we are not using delays it writes for each call to the storage changing methods
        verify(btcReleaseClientFileStorage, times(2)).write(any(BtcReleaseClientFileData.class));

//        FileStorageInfo storageInfo = new BtcReleaseClientFileStorageInfo(getFedNodeSystemProperties());
//        BtcReleaseClientFileStorage storage = new BtcReleaseClientFileStorageImpl(storageInfo);
//        BtcReleaseClientFileReadResult readResult =
//            storage.read(ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString()));
//
//        assertTrue(readResult.getSuccess());
//
//        BtcReleaseClientFileData data = readResult.getData();
//
//        assertTrue(data.getBestBlockHash().isPresent());
//        assertEquals(bestBlockHash, data.getBestBlockHash().get());
//        assertEquals(rskTxHash, data.getReleaseHashesMap().get(btcTxHash));
    }

    @Test
    public void multiple_sets_delay_writing()
        throws IOException, InvalidStorageFileException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);
        Keccak256 bestBlockHash = createHash(2);

        Sha256Hash btcTxHash2 = Sha256Hash.of(new byte[]{2});
        Keccak256 rskTxHash2 = createHash(3);
        Keccak256 bestBlockHash2 = createHash(4);

        // Each operation will signal a writing request, given we perform 2 operations on each test I'll set a max of 4 delays
        int maxDelays = 4;

        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        ScheduledFuture task = mock(ScheduledFuture.class);
        AtomicInteger calls = new AtomicInteger(1);
        // Execute after the maxDelay is reached
        doAnswer((InvocationOnMock a) -> {
            if (calls.getAndIncrement() >= maxDelays) {
                ((Runnable)a.getArgument(0)).run();
            }
            return task;
        }).when(executorService).schedule(any(Runnable.class), anyLong(), any());

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, new BtcReleaseClientFileData()));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                executorService,
                btcReleaseClientFileStorage,
                400,
                maxDelays
            );

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);
        storageAccessor.setBestBlockHash(bestBlockHash);

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash2, rskTxHash2);

        verify(btcReleaseClientFileStorage, never()).write(any(BtcReleaseClientFileData.class));

        // the forth call should trigger the writing
        storageAccessor.setBestBlockHash(bestBlockHash2);

        verify(btcReleaseClientFileStorage, times(1)).write(any(BtcReleaseClientFileData.class));

        // It should have cancelled the overlapping tasks
        verify(task, times(3)).cancel(anyBoolean());
    }

    @Test
    public void forces_writing_after_max_delay()
        throws InvalidStorageFileException, IOException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);
        Keccak256 bestBlockHash = createHash(2);

        Sha256Hash btcTxHash2 = Sha256Hash.of(new byte[]{2});
        Keccak256 rskTxHash2 = createHash(3);
        Keccak256 bestBlockHash2 = createHash(4);

        // Configure 2 delay at most to exercise the maxDelay limitation
        int maxDelays = 2;

        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        ScheduledFuture task = mock(ScheduledFuture.class);
        AtomicInteger calls = new AtomicInteger(1);
        doAnswer((InvocationOnMock a) -> {
            if (calls.getAndIncrement() > maxDelays) {
                // The schedule function shouldn't be called more than maxDelay with this Executor mock
                fail();
            }
            return task;
        }).when(executorService).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        BtcReleaseClientFileStorage btcReleaseClientFileStorage = mock(BtcReleaseClientFileStorage.class);
        when(btcReleaseClientFileStorage.read(any(NetworkParameters.class)))
            .thenReturn(new BtcReleaseClientFileReadResult(true, new BtcReleaseClientFileData()));

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(
                getFedNodeSystemProperties(),
                executorService,
                btcReleaseClientFileStorage,
                500,
                maxDelays
            );

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash); // First write delayed
        storageAccessor.setBestBlockHash(bestBlockHash); // Extends delay
        storageAccessor.putBtcTxHashRskTxHash(btcTxHash2, rskTxHash2); // Triggers a writing
        storageAccessor.setBestBlockHash(bestBlockHash2); // Triggers a writing again
        verify(task, times(1)).cancel(false); // It should have cancelled the first task only
    }

    private FedNodeSystemProperties getFedNodeSystemProperties() {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        return fedNodeSystemProperties;
    }

    private ScheduledExecutorService getImmediateTaskExecutor() {
        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        // Ignore delay and execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)a.getArgument(0)).run();
            return null;
        }).when(executorService).schedule(any(Runnable.class), anyLong(), any());

        return executorService;
    }

}

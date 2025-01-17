package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.LogInfo;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

public class BtcReleaseClientStorageSynchronizerTest {

    @Test
    public void isSynced_returns_false_after_instantiation() {
        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            new BtcReleaseClientStorageSynchronizer(
                mock(BlockStore.class),
                mock(ReceiptStore.class),
                mock(NodeBlockProcessor.class),
                mock(BtcReleaseClientStorageAccessor.class),
                mock(ScheduledExecutorService.class), // Don't specify behavior for syncing to avoid syncing
                1000,
                100,
                6_000
            );

        assertFalse(storageSynchronizer.isSynced());
    }

    @Test
    public void processBlock_before_sync_doesnt_do_anything() {
        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            new BtcReleaseClientStorageSynchronizer(
                mock(BlockStore.class),
                mock(ReceiptStore.class),
                mock(NodeBlockProcessor.class),
                storageAccessor,
                mock(ScheduledExecutorService.class), // Don't specify behavior for syncing to avoid syncing
                1000,
                100,
                6_000
            );

        assertFalse(storageSynchronizer.isSynced());

        storageSynchronizer.processBlock(mock(Block.class), Collections.emptyList());

        verifyZeroInteractions(storageAccessor);
    }

    @Test
    public void isSynced_returns_true_after_sync() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        when(firstBlock.getNumber()).thenReturn(0L);
        when(blockStore.getChainBlockByNumber(0L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(1L);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(bestBlock);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            new BtcReleaseClientStorageSynchronizer(
                blockStore,
                mock(ReceiptStore.class),
                mock(NodeBlockProcessor.class),
                mock(BtcReleaseClientStorageAccessor.class),
                mockedExecutor,
                0,
                1,
                6_000);

        assertTrue(storageSynchronizer.isSynced());
    }

    @Test
    public void syncs_from_last_stored_block() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        Keccak256 firstHash = createHash(0);
        when(firstBlock.getHash()).thenReturn(firstHash);
        when(firstBlock.getNumber()).thenReturn(1L);
        when(blockStore.getBlockByHash(firstHash.getBytes())).thenReturn(firstBlock);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getHash()).thenReturn(createHash(1));
        when(bestBlock.getNumber()).thenReturn(2L);
        when(blockStore.getChainBlockByNumber(2L)).thenReturn(bestBlock);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);
        when(storageAccessor.getBestBlockHash()).thenReturn(Optional.of(firstHash));

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());


        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            new BtcReleaseClientStorageSynchronizer(
                blockStore,
                mock(ReceiptStore.class),
                mock(NodeBlockProcessor.class),
                storageAccessor,
                mockedExecutor,
                0,
                1,
                6_000);

        assertTrue(storageSynchronizer.isSynced());

        verify(storageAccessor, never()).setBestBlockHash(firstBlock.getHash());
        verify(storageAccessor).setBestBlockHash(bestBlock.getHash());
    }

    @Test
    public void processBlock_ok() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        when(firstBlock.getNumber()).thenReturn(0L);
        Keccak256 firstHash = createHash(0);
        when(firstBlock.getHash()).thenReturn(firstHash);
        when(blockStore.getChainBlockByNumber(0L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(1L);
        Keccak256 secondHash = createHash(1);
        when(bestBlock.getHash()).thenReturn(secondHash);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(bestBlock);

        Block newBlock = mock(Block.class);
        when(newBlock.getNumber()).thenReturn(2L);
        Keccak256 thirdHash = createHash(2);
        when(newBlock.getHash()).thenReturn(thirdHash);
        when(blockStore.getChainBlockByNumber(2L)).thenReturn(newBlock);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = new BridgeEventLoggerImpl(
            BridgeRegTestConstants.getInstance(),
            mock(ActivationConfig.ForBlock.class),
            logs
        );

        Keccak256 value = createHash(3);
        Sha256Hash key = Sha256Hash.ZERO_HASH;

        Transaction releaseRskTx = mock(Transaction.class);
        when(releaseRskTx.getHash()).thenReturn(value);

        BtcTransaction releaseBtcTx = mock(BtcTransaction.class);
        when(releaseBtcTx.getHash()).thenReturn(key);

        // Event info
        bridgeEventLogger.logReleaseBtcRequested(
            value.getBytes(),
            releaseBtcTx,
            Coin.COIN
        );
        when(receipt.getLogInfoList()).thenReturn(logs);
        when(receipt.getTransaction()).thenReturn(releaseRskTx);
        List<TransactionReceipt> receipts = Arrays.asList(receipt);

        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            new BtcReleaseClientStorageSynchronizer(
                blockStore,
                mock(ReceiptStore.class),
                mock(NodeBlockProcessor.class),
                storageAccessor,
                mockedExecutor,
                0,
                1,
                6_000);

        // Verify sync
        assertTrue(storageSynchronizer.isSynced());

        // Process a block that contains a release_requested event
        storageSynchronizer.processBlock(newBlock, receipts);

        // Verify it is correctly stored
        ArgumentCaptor<Keccak256> captor = ArgumentCaptor.forClass(Keccak256.class);
        verify(storageAccessor, times(3)).setBestBlockHash(captor.capture());
        List<Keccak256> calls = captor.getAllValues();
        assertEquals(firstHash, calls.get(0));
        assertEquals(secondHash, calls.get(1));
        assertEquals(thirdHash, calls.get(2));
        verify(storageAccessor, times(1)).putBtcTxHashRskTxHash(key, value);


    }

}

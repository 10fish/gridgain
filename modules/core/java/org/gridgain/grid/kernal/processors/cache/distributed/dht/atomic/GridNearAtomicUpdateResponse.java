/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.atomic;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.util.direct.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * DHT atomic cache near update response.
 */
public class GridNearAtomicUpdateResponse<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Cache message index. */
    public static final int CACHE_MSG_IDX = nextIndexId();

    /** Node ID this reply should be sent to. */
    @GridDirectTransient
    private UUID nodeId;

    /** Future version. */
    private GridCacheVersion futVer;

    /** Update error. */
    @GridDirectTransient
    private volatile GridMultiException err;

    /** Serialized error. */
    private byte[] errBytes;

    /** Return value. */
    @GridDirectTransient
    private GridCacheReturn<V> retVal;

    /** Serialized return value. */
    private byte[] retValBytes;

    /** Failed keys. */
    @GridToStringInclude
    @GridDirectTransient
    private volatile Collection<K> failedKeys;

    /** Serialized failed keys. */
    private byte[] failedKeysBytes;

    /** Keys that should be remapped. */
    @GridToStringInclude
    @GridDirectTransient
    private Collection<K> remapKeys;

    /** Serialized keys that should be remapped. */
    private byte[] remapKeysBytes;

    /** */
    @GridDirectCollection(int.class)
    private List<Integer> nearValsIdxs;

    /** */
    @GridDirectCollection(int.class)
    private List<Integer> skippedIdxs;

    /** Values to update. */
    @GridToStringInclude
    @GridDirectTransient
    private List<V> nearVals;

    /** Value bytes. */
    @GridToStringInclude
    @GridDirectCollection(GridCacheValueBytes.class)
    private List<GridCacheValueBytes> nearValBytes;

    /** */
    private GridCacheVersion ver;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearAtomicUpdateResponse() {
        // No-op.
    }

    /**
     * @param nodeId Node ID this reply should be sent to.
     * @param futVer Future version.
     */
    public GridNearAtomicUpdateResponse(UUID nodeId, GridCacheVersion futVer) {
        this.nodeId = nodeId;
        this.futVer = futVer;
    }

    /** {@inheritDoc} */
    @Override public int lookupIndex() {
        return CACHE_MSG_IDX;
    }

    /**
     * @return Node ID this response should be sent to.
     */
    public UUID nodeId() {
        return nodeId;
    }

    /**
     * @param nodeId Node ID.
     */
    public void nodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * @return Future version.
     */
    public GridCacheVersion futureVersion() {
        return futVer;
    }

    /**
     * @return Update error, if any.
     */
    public Throwable error() {
        return err;
    }

    /**
     * @return Collection of failed keys.
     */
    public Collection<K> failedKeys() {
        return failedKeys;
    }

    /**
     * @return Return value.
     */
    public GridCacheReturn<V> returnValue() {
        return retVal;
    }

    /**
     * @param retVal Return value.
     */
    public void returnValue(GridCacheReturn<V> retVal) {
        this.retVal = retVal;
    }

    /**
     * @param remapKeys Remap keys.
     */
    public void remapKeys(Collection<K> remapKeys) {
        this.remapKeys = remapKeys;
    }

    /**
     * @return Remap keys.
     */
    public Collection<K> remapKeys() {
        return remapKeys;
    }

    /**
     * Adds value to be put in near cache on originating node.
     *
     * @param keyIdx Key index.
     * @param val Value.
     * @param valBytes Value bytes.
     */
    public void addNearValue(int keyIdx, @Nullable V val, @Nullable byte[] valBytes) {
        if (nearValsIdxs == null) {
            nearValsIdxs = new ArrayList<>();
            nearValBytes = new ArrayList<>();
            nearVals = new ArrayList<>();
        }

        nearValsIdxs.add(keyIdx);
        nearVals.add(val);
        nearValBytes.add(valBytes != null ? GridCacheValueBytes.marshaled(valBytes) : null);
    }

    public void version(GridCacheVersion ver) {
        this.ver = ver;
    }

    public GridCacheVersion version() {
        return ver;
    }

    public void addSkippedIndex(int keyIdx) {
        if (skippedIdxs == null)
            skippedIdxs = new ArrayList<>();

        skippedIdxs.add(keyIdx);
    }

    @Nullable public List<Integer> skippedIndexes() {
        return skippedIdxs;
    }

   @Nullable public List<Integer> nearValuesIndexes() {
        return nearValsIdxs;
   }

    @Nullable public V nearValue(int idx) {
        return nearVals.get(idx);
    }

    @Nullable public byte[] nearValueBytes(int idx) {
        if (nearValBytes != null) {
            GridCacheValueBytes valBytes0 = nearValBytes.get(idx);

            if (valBytes0 != null && !valBytes0.isPlain())
                return valBytes0.get();
        }

        return null;
    }

    /**
     * Adds key to collection of failed keys.
     *
     * @param key Key to add.
     * @param e Error cause.
     */
    public synchronized void addFailedKey(K key, Throwable e) {
        if (failedKeys == null)
            failedKeys = new ConcurrentLinkedQueue<>();

        failedKeys.add(key);

        if (err == null)
            err = new GridMultiException("Failed to update keys on primary node.");

        if (e instanceof GridMultiException) {
            for (Throwable th : ((GridMultiException)e).nestedCauses())
                err.add(th);
        }
        else
            err.add(e);
    }

    /**
     * Adds keys to collection of failed keys.
     *
     * @param keys Key to add.
     * @param e Error cause.
     */
    public synchronized void addFailedKeys(Collection<K> keys, Throwable e) {
        if (failedKeys == null)
            failedKeys = new ArrayList<>(keys.size());

        failedKeys.addAll(keys);

        if (err == null)
            err = new GridMultiException("Failed to update keys on primary node.");

        if (e instanceof GridMultiException) {
            for (Throwable th : ((GridMultiException)e).nestedCauses())
                err.add(th);
        }
        else
            err.add(e);
    }

    /** {@inheritDoc} */
    @Override public void prepareMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.prepareMarshal(ctx);

        if (err != null)
            errBytes = ctx.marshaller().marshal(err);

        if (retVal != null)
            retValBytes = ctx.marshaller().marshal(retVal);

        if (failedKeys != null)
            failedKeysBytes = ctx.marshaller().marshal(failedKeys);

        if (remapKeys != null)
            remapKeysBytes = ctx.marshaller().marshal(remapKeys);

        nearValBytes = marshalValuesCollection(nearVals, ctx);
    }

    /** {@inheritDoc} */
    @Override public void finishUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.finishUnmarshal(ctx, ldr);

        if (errBytes != null)
            err = ctx.marshaller().unmarshal(errBytes, ldr);

        if (retValBytes != null)
            retVal = ctx.marshaller().unmarshal(retValBytes, ldr);

        if (failedKeysBytes != null)
            failedKeys = ctx.marshaller().unmarshal(failedKeysBytes, ldr);

        if (remapKeysBytes != null)
            remapKeys = ctx.marshaller().unmarshal(remapKeysBytes, ldr);

        nearVals = unmarshalValueBytesCollection(nearValBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneCallsConstructors"})
    @Override public GridTcpCommunicationMessageAdapter clone() {
        GridNearAtomicUpdateResponse _clone = new GridNearAtomicUpdateResponse();

        clone0(_clone);

        return _clone;
    }

    /** {@inheritDoc} */
    @Override protected void clone0(GridTcpCommunicationMessageAdapter _msg) {
        super.clone0(_msg);

        GridNearAtomicUpdateResponse _clone = (GridNearAtomicUpdateResponse)_msg;

        _clone.nodeId = nodeId;
        _clone.futVer = futVer;
        _clone.err = err;
        _clone.errBytes = errBytes;
        _clone.retVal = retVal;
        _clone.retValBytes = retValBytes;
        _clone.failedKeys = failedKeys;
        _clone.failedKeysBytes = failedKeysBytes;
        _clone.remapKeys = remapKeys;
        _clone.remapKeysBytes = remapKeysBytes;
        _clone.nearValsIdxs = nearValsIdxs;
        _clone.skippedIdxs = skippedIdxs;
        _clone.nearVals = nearVals;
        _clone.nearValBytes = nearValBytes;
        _clone.ver = ver;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("fallthrough")
    @Override public boolean writeTo(ByteBuffer buf) {
        commState.setBuffer(buf);

        if (!super.writeTo(buf))
            return false;

        if (!commState.typeWritten) {
            if (!commState.putByte(directType()))
                return false;

            commState.typeWritten = true;
        }

        switch (commState.idx) {
            case 2:
                if (!commState.putByteArray(errBytes))
                    return false;

                commState.idx++;

            case 3:
                if (!commState.putByteArray(failedKeysBytes))
                    return false;

                commState.idx++;

            case 4:
                if (!commState.putCacheVersion(futVer))
                    return false;

                commState.idx++;

            case 5:
                if (nearValBytes != null) {
                    if (commState.it == null) {
                        if (!commState.putInt(nearValBytes.size()))
                            return false;

                        commState.it = nearValBytes.iterator();
                    }

                    while (commState.it.hasNext() || commState.cur != NULL) {
                        if (commState.cur == NULL)
                            commState.cur = commState.it.next();

                        if (!commState.putValueBytes((GridCacheValueBytes)commState.cur))
                            return false;

                        commState.cur = NULL;
                    }

                    commState.it = null;
                } else {
                    if (!commState.putInt(-1))
                        return false;
                }

                commState.idx++;

            case 6:
                if (nearValsIdxs != null) {
                    if (commState.it == null) {
                        if (!commState.putInt(nearValsIdxs.size()))
                            return false;

                        commState.it = nearValsIdxs.iterator();
                    }

                    while (commState.it.hasNext() || commState.cur != NULL) {
                        if (commState.cur == NULL)
                            commState.cur = commState.it.next();

                        if (!commState.putInt((int)commState.cur))
                            return false;

                        commState.cur = NULL;
                    }

                    commState.it = null;
                } else {
                    if (!commState.putInt(-1))
                        return false;
                }

                commState.idx++;

            case 7:
                if (!commState.putByteArray(remapKeysBytes))
                    return false;

                commState.idx++;

            case 8:
                if (!commState.putByteArray(retValBytes))
                    return false;

                commState.idx++;

            case 9:
                if (skippedIdxs != null) {
                    if (commState.it == null) {
                        if (!commState.putInt(skippedIdxs.size()))
                            return false;

                        commState.it = skippedIdxs.iterator();
                    }

                    while (commState.it.hasNext() || commState.cur != NULL) {
                        if (commState.cur == NULL)
                            commState.cur = commState.it.next();

                        if (!commState.putInt((int)commState.cur))
                            return false;

                        commState.cur = NULL;
                    }

                    commState.it = null;
                } else {
                    if (!commState.putInt(-1))
                        return false;
                }

                commState.idx++;

            case 10:
                if (!commState.putCacheVersion(ver))
                    return false;

                commState.idx++;

        }

        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("fallthrough")
    @Override public boolean readFrom(ByteBuffer buf) {
        commState.setBuffer(buf);

        if (!super.readFrom(buf))
            return false;

        switch (commState.idx) {
            case 2:
                byte[] errBytes0 = commState.getByteArray();

                if (errBytes0 == BYTE_ARR_NOT_READ)
                    return false;

                errBytes = errBytes0;

                commState.idx++;

            case 3:
                byte[] failedKeysBytes0 = commState.getByteArray();

                if (failedKeysBytes0 == BYTE_ARR_NOT_READ)
                    return false;

                failedKeysBytes = failedKeysBytes0;

                commState.idx++;

            case 4:
                GridCacheVersion futVer0 = commState.getCacheVersion();

                if (futVer0 == CACHE_VER_NOT_READ)
                    return false;

                futVer = futVer0;

                commState.idx++;

            case 5:
                if (commState.readSize == -1) {
                    if (buf.remaining() < 4)
                        return false;

                    commState.readSize = commState.getInt();
                }

                if (commState.readSize >= 0) {
                    if (nearValBytes == null)
                        nearValBytes = new ArrayList<>(commState.readSize);

                    for (int i = commState.readItems; i < commState.readSize; i++) {
                        GridCacheValueBytes _val = commState.getValueBytes();

                        if (_val == VAL_BYTES_NOT_READ)
                            return false;

                        nearValBytes.add((GridCacheValueBytes)_val);

                        commState.readItems++;
                    }
                }

                commState.readSize = -1;
                commState.readItems = 0;

                commState.idx++;

            case 6:
                if (commState.readSize == -1) {
                    if (buf.remaining() < 4)
                        return false;

                    commState.readSize = commState.getInt();
                }

                if (commState.readSize >= 0) {
                    if (nearValsIdxs == null)
                        nearValsIdxs = new ArrayList<>(commState.readSize);

                    for (int i = commState.readItems; i < commState.readSize; i++) {
                        if (buf.remaining() < 4)
                            return false;

                        int _val = commState.getInt();

                        nearValsIdxs.add((Integer)_val);

                        commState.readItems++;
                    }
                }

                commState.readSize = -1;
                commState.readItems = 0;

                commState.idx++;

            case 7:
                byte[] remapKeysBytes0 = commState.getByteArray();

                if (remapKeysBytes0 == BYTE_ARR_NOT_READ)
                    return false;

                remapKeysBytes = remapKeysBytes0;

                commState.idx++;

            case 8:
                byte[] retValBytes0 = commState.getByteArray();

                if (retValBytes0 == BYTE_ARR_NOT_READ)
                    return false;

                retValBytes = retValBytes0;

                commState.idx++;

            case 9:
                if (commState.readSize == -1) {
                    if (buf.remaining() < 4)
                        return false;

                    commState.readSize = commState.getInt();
                }

                if (commState.readSize >= 0) {
                    if (skippedIdxs == null)
                        skippedIdxs = new ArrayList<>(commState.readSize);

                    for (int i = commState.readItems; i < commState.readSize; i++) {
                        if (buf.remaining() < 4)
                            return false;

                        int _val = commState.getInt();

                        skippedIdxs.add((Integer)_val);

                        commState.readItems++;
                    }
                }

                commState.readSize = -1;
                commState.readItems = 0;

                commState.idx++;

            case 10:
                GridCacheVersion ver0 = commState.getCacheVersion();

                if (ver0 == CACHE_VER_NOT_READ)
                    return false;

                ver = ver0;

                commState.idx++;

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public byte directType() {
        return 40;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearAtomicUpdateResponse.class, this, "parent");
    }
}

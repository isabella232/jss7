/*
 * JBoss, Home of Professional Open Source
 * Copyright XXXX, Red Hat Middleware LLC, and individual contributors as indicated
 * by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.protocols.ss7.sccp.impl.ud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Arrays;

import org.mobicents.protocols.ss7.sccp.impl.parameter.ImportanceImpl;
import org.mobicents.protocols.ss7.sccp.impl.parameter.ProtocolClassImpl;
import org.mobicents.protocols.ss7.sccp.impl.parameter.SccpAddressCodec;
import org.mobicents.protocols.ss7.sccp.impl.parameter.SegmentationImpl;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.sccp.ud.Parameter;

/**
 * See Q.713 4.18
 * 
 * @author Oleg Kulikov
 * @author baranowb
 */
public class XUnitDataImpl extends UnitDataImpl {

    private static final byte _MT = 17;
    public static final int HOP_COUNT_NOT_SET = 16;
    public static final int HOP_COUNT_LOW_ = 0;
    public static final int HOP_COUNT_HIGH_ = 16;    // //////////////////
    // Fixed parts //
    // //////////////////
    /**
     * See Q.713 3.18
     */
    private byte hopCounter = HOP_COUNT_NOT_SET;
    //private ProtocolClassImpl pClass;

    // //////////////////
    // Variable parts //
    // //////////////////
    //private SccpAddressCodec calledParty;
    //private SccpAddressCodec callingParty;
    //private byte[] data;
    // //////////////////
    // Optional parts //
    // //////////////////
    private SegmentationImpl segmentation;
    private ImportanceImpl importance;

    private SccpAddressCodec addressCodec = new SccpAddressCodec();
    
    // EOP
    /** Creates a new instance of UnitData */
    public XUnitDataImpl() {
    }

    public XUnitDataImpl(byte hopCounter, ProtocolClassImpl pClass, SccpAddress calledParty, SccpAddress callingParty, byte[] data) {
        super();
        this.hopCounter = hopCounter;
        this.pClass = pClass;
        super.calledParty = (SccpAddress) calledParty;
        super.callingParty = (SccpAddress) callingParty;
        super.data = data;
    }

    public XUnitDataImpl(byte hopCounter, ProtocolClassImpl pClass, SccpAddress calledParty, SccpAddress callingParty, byte[] data,
            SegmentationImpl segmentation, ImportanceImpl importance) {
        super();
        this.hopCounter = hopCounter;
        super.pClass = pClass;
        super.calledParty = calledParty;
        super.callingParty =  callingParty;
        super.data = data;
        this.segmentation = segmentation;
        this.importance = importance;
    }

    public byte getHopCounter() {
        return hopCounter;
    }

    public void setHopCounter(byte hopCounter) {
        this.hopCounter = hopCounter;
    }

    public SegmentationImpl getSegmentation() {
        return segmentation;
    }

    public void setSegmentation(SegmentationImpl segmentation) {
        this.segmentation = segmentation;
    }

    public ImportanceImpl getImportance() {
        return importance;
    }

    public void setImportance(ImportanceImpl importance) {
        this.importance = importance;
    }

    @Override
    public void encode(OutputStream out) throws IOException {
        out.write(_MT);

        pClass.encode(out);
        if (this.hopCounter == HOP_COUNT_NOT_SET) {
            throw new IOException("Failed parsing, hop counter is not set.");
        }
        out.write(this.hopCounter);

        byte[] cdp = addressCodec.encode(calledParty);
        byte[] cnp = addressCodec.encode(callingParty);

        // we have 4 pointers, cdp,cnp,data and optionalm, cdp starts after 4
        // octests than
        int len = 4;
        out.write(len);

        len = (cdp.length + len);
        out.write(len);

        len += (cnp.length);
        out.write(len);
        boolean optionalPresent = false;
        if (segmentation != null || importance != null) {
            len += (data.length);
            out.write(len);
            optionalPresent = true;
        } else {
            // in case there is no optional
            out.write(0);
        }

        out.write((byte) cdp.length);
        out.write(cdp);

        out.write((byte) cnp.length);
        out.write(cnp);

        out.write((byte) data.length);
        out.write(data);

        if (segmentation != null) {
            optionalPresent = true;
            out.write(Parameter.SEGMENTATION);
            byte[] b = segmentation.encode();
            out.write(b.length);
            out.write(b);
        }

        if (importance != null) {
            optionalPresent = true;
            out.write(Parameter.IMPORTANCE);
            byte[] b = importance.encode();
            out.write(b.length);
            out.write(b);
        }

        if (optionalPresent) {
            out.write(0x00);
        }

    }

    @Override
    public void decode(InputStream in) throws IOException {

        pClass = new ProtocolClassImpl();
        pClass.decode(in);

        this.hopCounter = (byte) in.read();
        if (this.hopCounter >= HOP_COUNT_HIGH_ || this.hopCounter <= HOP_COUNT_LOW_) {
            throw new IOException("Hop Counter must be between 1 and 5, it is: " + this.hopCounter);
        }

        int pointer = in.read() & 0xff;
        in.mark(in.available());
        if (pointer - 1 != in.skip(pointer - 1)) {
            throw new IOException("Not enough data in buffer");
        }
        int len = in.read() & 0xff;

        byte[] buffer = new byte[len];
        in.read(buffer);

        calledParty = addressCodec.decode(buffer);

        in.reset();

        pointer = in.read() & 0xff;

        in.mark(in.available());

        if (pointer - 1 != in.skip(pointer - 1)) {
            throw new IOException("Not enough data in buffer");
        }
        len = in.read() & 0xff;

        buffer = new byte[len];
        in.read(buffer);

        callingParty = addressCodec.decode(buffer);

        in.reset();
        pointer = in.read() & 0xff;
        in.mark(in.available());
        if (pointer - 1 != in.skip(pointer - 1)) {
            throw new IOException("Not enough data in buffer");
        }
        len = in.read() & 0xff;

        data = new byte[len];
        in.read(data);

        in.reset();
        pointer = in.read() & 0xff;
        in.mark(in.available());

        if (pointer == 0) {
            // we are done
            return;
        }
        if (pointer - 1 != in.skip(pointer - 1)) {
            throw new IOException("Not enough data in buffer");
        }

        //FIXME: detect if there is only EOP present?
        int paramCode = 0;
        //                                      EOP
        while ((paramCode = in.read() & 0xFF) != 0) {
            len = in.read() & 0xff;
            buffer = new byte[len];
            in.read(buffer);
            this.decodeOptional(paramCode, buffer);

        // we should have one octet more here
        }

    }

    private void decodeOptional(int code, byte[] buffer) throws IOException {

        switch (code) {
            case Parameter.SEGMENTATION :
                this.segmentation = new SegmentationImpl();
                this.segmentation.decode(buffer);
                break;
            case Parameter.IMPORTANCE : 
                this.importance = new ImportanceImpl();
                this.importance.decode(buffer);
                break;

            default:
                throw new IOException("Uknown optional parameter code: " + code);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((calledParty == null) ? 0 : calledParty.hashCode());
        result = prime * result + ((callingParty == null) ? 0 : callingParty.hashCode());
        result = prime * result + Arrays.hashCode(data);
        result = prime * result + hopCounter;
        result = prime * result + ((importance == null) ? 0 : importance.hashCode());
        result = prime * result + ((pClass == null) ? 0 : pClass.hashCode());
        result = prime * result + ((segmentation == null) ? 0 : segmentation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        XUnitDataImpl other = (XUnitDataImpl) obj;
        if (calledParty == null) {
            if (other.calledParty != null) {
                return false;
            }
        } else if (!calledParty.equals(other.calledParty)) {
            return false;
        }
        if (callingParty == null) {
            if (other.callingParty != null) {
                return false;
            }
        } else if (!callingParty.equals(other.callingParty)) {
            return false;
        }
        if (!Arrays.equals(data, other.data)) {
            return false;
        }
        if (hopCounter != other.hopCounter) {
            return false;
        }
        if (importance == null) {
            if (other.importance != null) {
                return false;
            }
        } else if (!importance.equals(other.importance)) {
            return false;
        }
        if (pClass == null) {
            if (other.pClass != null) {
                return false;
            }
        } else if (!pClass.equals(other.pClass)) {
            return false;
        }
        if (segmentation == null) {
            if (other.segmentation != null) {
                return false;
            }
        } else if (!segmentation.equals(other.segmentation)) {
            return false;
        }
        return true;
    }
}





package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationStatusTest {

    @Test
    void in_progress_wire_format() {
        assertEquals("in-progress", InvestigationStatus.IN_PROGRESS.toWireFormat());
    }

    @Test
    void completed_wire_format() {
        assertEquals("completed", InvestigationStatus.COMPLETED.toWireFormat());
    }

    @Test
    void from_wire_format_in_progress() {
        assertEquals(InvestigationStatus.IN_PROGRESS, InvestigationStatus.fromWireFormat("in-progress"));
    }

    @Test
    void from_wire_format_completed() {
        assertEquals(InvestigationStatus.COMPLETED, InvestigationStatus.fromWireFormat("completed"));
    }

    @Test
    void failed_wire_format() {
        assertEquals("failed", InvestigationStatus.FAILED.toWireFormat());
    }

    @Test
    void cancelled_wire_format() {
        assertEquals("cancelled", InvestigationStatus.CANCELLED.toWireFormat());
    }

    @Test
    void suspended_wire_format() {
        assertEquals("suspended", InvestigationStatus.SUSPENDED.toWireFormat());
    }

    @Test
    void from_wire_format_failed() {
        assertEquals(InvestigationStatus.FAILED, InvestigationStatus.fromWireFormat("failed"));
    }

    @Test
    void from_wire_format_cancelled() {
        assertEquals(InvestigationStatus.CANCELLED, InvestigationStatus.fromWireFormat("cancelled"));
    }

    @Test
    void from_wire_format_suspended() {
        assertEquals(InvestigationStatus.SUSPENDED, InvestigationStatus.fromWireFormat("suspended"));
    }
}

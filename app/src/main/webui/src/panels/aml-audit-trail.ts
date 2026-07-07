import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { ColumnDef } from '@casehubio/blocks-ui-data-table';
import '@casehubio/blocks-ui-data-table';
import type {
  AuditTrailEntry,
  AmlInclusionProof,
} from '../types.js';

interface VerificationResult {
  verified: boolean;
  proof?: AmlInclusionProof;
  error?: string;
}

@customElement('aml-audit-trail')
export class AmlAuditTrailPanel extends LitElement {
  @property() caseId = '';

  @state() private _entries: AuditTrailEntry[] = [];
  @state() private _loading = false;
  @state() private _error: string | null = null;

  @state() private _verificationResults = new Map<string, VerificationResult>();
  @state() private _expandedProofs = new Set<string>();

  private _columns: ColumnDef<AuditTrailEntry>[] = [
    {
      key: 'entryType',
      header: 'Entry Type',
      getValue: (row) => row.entryType,
    },
    {
      key: 'actorId',
      header: 'Actor ID',
      getValue: (row) => row.actorId,
    },
    {
      key: 'actorRole',
      header: 'Role',
      getValue: (row) => row.actorRole,
    },
    {
      key: 'occurredAt',
      header: 'Occurred At',
      getValue: (row) => new Date(row.occurredAt).toLocaleString(),
    },
    {
      key: 'causedByEntryId',
      header: 'Caused By',
      getValue: (row) => row.causedByEntryId ? this._shortenUuid(row.causedByEntryId) : '—',
      render: (row) => {
        if (!row.causedByEntryId) {
          return html`<span>—</span>`;
        }
        return html`
          <a
            href="#"
            @click=${(e: Event) => this._scrollToEntry(e, row.causedByEntryId!)}
            class="link"
          >
            ${this._shortenUuid(row.causedByEntryId)}
          </a>
        `;
      },
    },
    {
      key: 'digest',
      header: 'Digest',
      getValue: (row) => row.digest.substring(0, 16),
      render: (row) => html`<code class="digest">${row.digest.substring(0, 16)}</code>`,
    },
    {
      key: 'verify',
      header: 'Verify',
      getValue: () => '',
      render: (row) => this._renderVerifyButton(row),
    },
  ];

  static override styles = css`
    :host {
      display: block;
      padding: var(--pages-space-4, 16px);
    }

    .section {
      margin-bottom: var(--pages-space-4, 16px);
    }

    .section-title {
      font-size: var(--pages-font-size-lg, 16px);
      font-weight: 600;
      color: var(--pages-neutral-11, #0a0a0a);
      margin-bottom: var(--pages-space-3, 12px);
    }

    .table-container {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      overflow: hidden;
      background: var(--pages-neutral-1, #ffffff);
    }

    .link {
      color: var(--pages-accent-9, #3b82f6);
      text-decoration: none;
      cursor: pointer;
    }

    .link:hover {
      text-decoration: underline;
    }

    .digest {
      font-family: 'Courier New', Courier, monospace;
      font-size: var(--pages-font-size-xs, 11px);
      background: var(--pages-neutral-2, #f5f5f5);
      padding: 2px 6px;
      border-radius: 3px;
    }

    .verify-btn {
      padding: 4px 12px;
      background: var(--pages-accent-3, #dbeafe);
      color: var(--pages-accent-9, #3b82f6);
      border: 1px solid var(--pages-accent-5, #93c5fd);
      border-radius: 4px;
      cursor: pointer;
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600;
      transition: all 0.15s ease;
    }

    .verify-btn:hover {
      background: var(--pages-accent-4, #bfdbfe);
    }

    .verify-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .verify-result {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 8px);
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 600;
    }

    .verify-result.success {
      color: var(--pages-success-9, #16a34a);
    }

    .verify-result.error {
      color: var(--pages-danger-9, #dc2626);
    }

    .verify-icon {
      width: 16px;
      height: 16px;
    }

    .expand-proof-btn {
      margin-left: var(--pages-space-2, 8px);
      padding: 2px 8px;
      background: transparent;
      border: 1px solid currentColor;
      border-radius: 3px;
      cursor: pointer;
      font-size: var(--pages-font-size-xs, 11px);
      color: inherit;
    }

    .expand-proof-btn:hover {
      background: var(--pages-neutral-2, #f5f5f5);
    }

    .proof-details {
      margin-top: var(--pages-space-3, 12px);
      padding: var(--pages-space-3, 12px);
      background: var(--pages-neutral-1, #ffffff);
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 6px;
      font-family: 'Courier New', Courier, monospace;
      font-size: var(--pages-font-size-xs, 11px);
      line-height: 1.6;
      overflow-x: auto;
    }

    .proof-row {
      display: flex;
      gap: var(--pages-space-2, 8px);
      margin-bottom: var(--pages-space-1, 4px);
    }

    .proof-label {
      font-weight: 600;
      color: var(--pages-neutral-8, #404040);
      min-width: 120px;
    }

    .proof-value {
      color: var(--pages-neutral-11, #0a0a0a);
      word-break: break-all;
    }

    .proof-siblings {
      margin-top: var(--pages-space-2, 8px);
      padding-left: var(--pages-space-3, 12px);
    }

    .sibling-item {
      margin-bottom: var(--pages-space-1, 4px);
      padding: var(--pages-space-1, 4px);
      background: var(--pages-neutral-2, #f5f5f5);
      border-radius: 3px;
    }

    /* Row styling based on actorRole */
    .role-human {
      background-color: var(--pages-accent-1, #eff6ff) !important;
    }

    .role-system {
      background-color: var(--pages-neutral-2, #f5f5f5) !important;
    }

    .skeleton {
      height: 300px;
      background: linear-gradient(
        90deg,
        var(--pages-neutral-2, #f5f5f5) 25%,
        var(--pages-neutral-3, #e5e5e5) 50%,
        var(--pages-neutral-2, #f5f5f5) 75%
      );
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
      border-radius: 8px;
    }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .error-card {
      background: var(--pages-error-1, #fef2f2);
      border: 1px solid var(--pages-error-4, #fca5a5);
      border-radius: 8px;
      padding: var(--pages-space-4, 16px);
      color: var(--pages-error-9, #dc2626);
    }

    .error-card button {
      margin-top: var(--pages-space-2, 8px);
      padding: var(--pages-space-1, 4px) var(--pages-space-3, 12px);
      background: var(--pages-error-9, #dc2626);
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
    }

    .error-card button:hover {
      background: var(--pages-error-10, #b91c1c);
    }

    .placeholder {
      background: var(--pages-neutral-2, #f5f5f5);
      border: 1px dashed var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      padding: var(--pages-space-6, 24px);
      text-align: center;
      color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-sm, 13px);
      font-style: italic;
    }
  `;

  override updated(changedProps: Map<string, unknown>): void {
    if (changedProps.has('caseId') && this.caseId) {
      this._verificationResults = new Map();
      this._expandedProofs = new Set();
      this._fetchAuditTrail();
    }
  }

  private async _fetchAuditTrail(): Promise<void> {
    this._loading = true;
    this._error = null;
    try {
      const response = await fetch(`/api/investigations/${this.caseId}/audit-trail`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._entries = await response.json();
    } catch (error) {
      this._error = error instanceof Error ? error.message : String(error);
    } finally {
      this._loading = false;
    }
  }

  private async _verifyEntry(entryId: string): Promise<void> {
    try {
      const response = await fetch(
        `/api/investigations/${this.caseId}/audit-trail/${entryId}/proof`
      );
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      const proof: AmlInclusionProof = await response.json();

      // Verification is done server-side; we just display the proof
      this._verificationResults.set(entryId, {
        verified: true,
        proof,
      });
    } catch (error) {
      this._verificationResults.set(entryId, {
        verified: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
    this.requestUpdate();
  }

  private _toggleProofExpansion(entryId: string): void {
    if (this._expandedProofs.has(entryId)) {
      this._expandedProofs.delete(entryId);
    } else {
      this._expandedProofs.add(entryId);
    }
    this.requestUpdate();
  }

  private _shortenUuid(uuid: string): string {
    return uuid.split('-')[0];
  }

  private _scrollToEntry(event: Event, targetEntryId: string): void {
    event.preventDefault();

    // Find the row element for the target entry
    const targetRow = this.shadowRoot?.querySelector(
      `[data-entry-id="${targetEntryId}"]`
    ) as HTMLElement;

    if (targetRow) {
      targetRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
      // Add a temporary highlight effect
      targetRow.style.backgroundColor = 'var(--pages-warning-2, #fef9c3)';
      setTimeout(() => {
        targetRow.style.backgroundColor = '';
      }, 2000);
    }
  }

  private _getRowClass(row: AuditTrailEntry): string {
    if (row.actorRole === 'HUMAN') return 'role-human';
    if (row.actorRole === 'SYSTEM') return 'role-system';
    return '';
  }

  private _renderVerifyButton(row: AuditTrailEntry) {
    const result = this._verificationResults.get(row.entryId);

    if (!result) {
      return html`
        <button
          class="verify-btn"
          @click=${() => this._verifyEntry(row.entryId)}
        >
          Verify
        </button>
      `;
    }

    const isExpanded = this._expandedProofs.has(row.entryId);

    if (result.verified && result.proof) {
      return html`
        <div>
          <div class="verify-result success">
            <svg class="verify-icon" viewBox="0 0 16 16" fill="none">
              <path
                d="M13.5 4L6 11.5L2.5 8"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span>Verified</span>
            <button
              class="expand-proof-btn"
              @click=${() => this._toggleProofExpansion(row.entryId)}
            >
              ${isExpanded ? 'Hide' : 'Show'} Proof
            </button>
          </div>
          ${isExpanded ? this._renderProofDetails(result.proof) : nothing}
        </div>
      `;
    }

    return html`
      <div class="verify-result error">
        <svg class="verify-icon" viewBox="0 0 16 16" fill="none">
          <path
            d="M12 4L4 12M4 4L12 12"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <span>Failed</span>
        ${result.error
          ? html`<span class="error-detail">(${result.error})</span>`
          : nothing}
      </div>
    `;
  }

  private _renderProofDetails(proof: AmlInclusionProof) {
    return html`
      <div class="proof-details">
        <div class="proof-row">
          <span class="proof-label">Entry Index:</span>
          <span class="proof-value">${proof.entryIndex}</span>
        </div>
        <div class="proof-row">
          <span class="proof-label">Tree Size:</span>
          <span class="proof-value">${proof.treeSize}</span>
        </div>
        <div class="proof-row">
          <span class="proof-label">Leaf Hash:</span>
          <span class="proof-value">${proof.leafHash}</span>
        </div>
        <div class="proof-row">
          <span class="proof-label">Tree Root:</span>
          <span class="proof-value">${proof.treeRoot}</span>
        </div>
        ${proof.siblings.length > 0
          ? html`
              <div class="proof-row">
                <span class="proof-label">Siblings:</span>
                <div class="proof-value">
                  <div class="proof-siblings">
                    ${proof.siblings.map(
                      (sibling, idx) => html`
                        <div class="sibling-item">
                          [${idx}] ${sibling.position}: ${sibling.hash}
                        </div>
                      `
                    )}
                  </div>
                </div>
              </div>
            `
          : nothing}
      </div>
    `;
  }

  override render() {
    if (this._loading) {
      return html`<div class="skeleton"></div>`;
    }

    if (this._error) {
      return html`
        <div class="error-card">
          <div>Failed to load audit trail: ${this._error}</div>
          <button @click=${this._fetchAuditTrail}>Retry</button>
        </div>
      `;
    }

    if (this._entries.length === 0) {
      return html`<div class="placeholder">No audit trail entries available</div>`;
    }

    return html`
      <div class="section">
        <div class="section-title">Ledger Entries</div>
        <div class="table-container">
          <pages-data-table
            .columns=${this._columns}
            .rows=${this._entries}
            .getRowKey=${(row: AuditTrailEntry) => row.entryId}
            .getRowClass=${this._getRowClass.bind(this)}
            mode="static"
            emptyMessage="No ledger entries found"
          ></pages-data-table>
        </div>
      </div>
    `;
  }
}

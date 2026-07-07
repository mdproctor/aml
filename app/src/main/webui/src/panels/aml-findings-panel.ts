import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type {
  InvestigationFindingsResponse,
  SpecialistFindingResponse,
} from '../types.js';

@customElement('aml-findings-panel')
export class AmlFindingsPanel extends LitElement {
  @property() caseId = '';

  @state() private _findings: InvestigationFindingsResponse | null = null;
  @state() private _loading = false;
  @state() private _error: string | null = null;

  @state() private _expandedSections = new Set<string>([
    'entity-resolution',
    'pattern-analysis',
    'osint-screening',
    'sar-narrative',
  ]);

  static override styles = css`
    :host {
      display: block;
      padding: var(--pages-space-4, 16px);
    }

    .section {
      margin-bottom: var(--pages-space-4, 16px);
    }

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: var(--pages-space-3, 12px);
      background: var(--pages-neutral-2, #f5f5f5);
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 8px;
      cursor: pointer;
      user-select: none;
      transition: background-color 0.15s ease;
    }

    .section-header:hover {
      background: var(--pages-neutral-3, #e5e5e5);
    }

    .section-header-left {
      display: flex;
      align-items: center;
      gap: var(--pages-space-3, 12px);
    }

    .section-title {
      font-size: var(--pages-font-size-md, 14px);
      font-weight: 600;
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .chevron {
      width: 16px;
      height: 16px;
      transition: transform 0.2s ease;
      color: var(--pages-neutral-7, #525252);
    }

    .chevron.expanded {
      transform: rotate(90deg);
    }

    .section-content {
      padding: var(--pages-space-4, 16px);
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-top: none;
      border-radius: 0 0 8px 8px;
      background: var(--pages-neutral-1, #ffffff);
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: var(--pages-font-size-xs, 11px);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .badge.completed {
      background: var(--pages-success-3, #dcfce7);
      color: var(--pages-success-9, #16a34a);
    }

    .badge.pending {
      background: var(--pages-warning-3, #fef3c7);
      color: var(--pages-warning-9, #d97706);
    }

    .badge.declined {
      background: var(--pages-neutral-3, #e5e5e5);
      color: var(--pages-neutral-8, #404040);
    }

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .status-dot.completed {
      background: var(--pages-success-9, #16a34a);
    }

    .status-dot.pending {
      background: var(--pages-warning-9, #d97706);
    }

    .status-dot.declined {
      background: var(--pages-neutral-8, #404040);
    }

    .result-grid {
      display: grid;
      gap: var(--pages-space-2, 8px);
    }

    .result-row {
      display: flex;
      justify-content: space-between;
      padding: var(--pages-space-2, 8px) 0;
      border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
    }

    .result-row:last-child {
      border-bottom: none;
    }

    .result-label {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-8, #404040);
      font-weight: 500;
    }

    .result-value {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-11, #0a0a0a);
      text-align: right;
      font-weight: 400;
    }

    .result-value.bool-true {
      color: var(--pages-success-9, #16a34a);
      font-weight: 600;
    }

    .result-value.bool-false {
      color: var(--pages-neutral-7, #525252);
    }

    .narrative-block {
      background: var(--pages-neutral-2, #f5f5f5);
      border-left: 4px solid var(--pages-accent-6, #93c5fd);
      padding: var(--pages-space-4, 16px);
      border-radius: 4px;
      font-size: var(--pages-font-size-sm, 13px);
      line-height: 1.6;
      color: var(--pages-neutral-11, #0a0a0a);
      white-space: pre-wrap;
      font-family: ui-serif, Georgia, Cambria, "Times New Roman", Times, serif;
    }

    .declined-notice {
      background: var(--pages-warning-2, #fef9c3);
      border: 1px solid var(--pages-warning-4, #fde047);
      border-radius: 6px;
      padding: var(--pages-space-3, 12px);
      margin-top: var(--pages-space-3, 12px);
    }

    .declined-title {
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 600;
      color: var(--pages-warning-10, #b45309);
      margin-bottom: var(--pages-space-2, 8px);
    }

    .declined-reason {
      font-size: var(--pages-font-size-sm, 13px);
      color: var(--pages-neutral-11, #0a0a0a);
    }

    .skeleton {
      height: 200px;
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
      this._fetchFindings();
    }
  }

  private async _fetchFindings(): Promise<void> {
    this._loading = true;
    this._error = null;
    try {
      const response = await fetch(`/api/investigations/${this.caseId}/findings`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      this._findings = await response.json();
    } catch (error) {
      this._error = error instanceof Error ? error.message : String(error);
    } finally {
      this._loading = false;
    }
  }

  private _toggleSection(sectionId: string): void {
    if (this._expandedSections.has(sectionId)) {
      this._expandedSections.delete(sectionId);
    } else {
      this._expandedSections.add(sectionId);
    }
    this.requestUpdate();
  }

  override render() {
    if (this._loading) {
      return html`<div class="skeleton"></div>`;
    }

    if (this._error) {
      return html`
        <div class="error-card">
          <div>Failed to load findings: ${this._error}</div>
          <button @click=${this._fetchFindings}>Retry</button>
        </div>
      `;
    }

    if (!this._findings) {
      return html`<div class="placeholder">No findings available</div>`;
    }

    return html`
      ${this._renderFindingSection(
        'entity-resolution',
        'Entity Resolution',
        this._findings.entityResolution
      )}
      ${this._renderFindingSection(
        'pattern-analysis',
        'Pattern Analysis',
        this._findings.patternAnalysis
      )}
      ${this._renderFindingSection(
        'osint-screening',
        'OSINT Screening',
        this._findings.osintScreening
      )}
      ${this._renderFindingSection(
        'sar-narrative',
        'SAR Drafting',
        this._findings.sarNarrative
      )}
    `;
  }

  private _renderFindingSection(
    id: string,
    title: string,
    finding: SpecialistFindingResponse
  ) {
    const isExpanded = this._expandedSections.has(id);
    const statusClass = finding.status.toLowerCase();
    const statusDotClass = statusClass === 'declined' ? 'declined' : statusClass;

    return html`
      <div class="section">
        <div class="section-header" @click=${() => this._toggleSection(id)}>
          <div class="section-header-left">
            <svg class="chevron ${isExpanded ? 'expanded' : ''}" viewBox="0 0 16 16" fill="none">
              <path d="M6 4L10 8L6 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span class="section-title">${title}</span>
            <span class="badge ${statusClass}">
              <span class="status-dot ${statusDotClass}"></span>
              ${finding.status}
            </span>
          </div>
        </div>

        ${isExpanded
          ? html`
              <div class="section-content">
                ${finding.status === 'DECLINED'
                  ? this._renderDeclinedContent(finding)
                  : finding.status === 'PENDING'
                  ? html`<div class="placeholder">Specialist task pending</div>`
                  : finding.result
                  ? id === 'sar-narrative'
                    ? this._renderNarrativeContent(finding.result)
                    : this._renderResultContent(finding.result)
                  : html`<div class="placeholder">No result data available</div>`}
              </div>
            `
          : nothing}
      </div>
    `;
  }

  private _renderDeclinedContent(finding: SpecialistFindingResponse) {
    const reason = finding.result?.reason ?? 'No reason provided';
    return html`
      <div class="declined-notice">
        <div class="declined-title">Task Declined by Specialist</div>
        <div class="declined-reason">${reason}</div>
      </div>
    `;
  }

  private _renderResultContent(result: Record<string, any>) {
    const entries = Object.entries(result).filter(([key]) => key !== 'reason');

    if (entries.length === 0) {
      return html`<div class="placeholder">No result data available</div>`;
    }

    return html`
      <div class="result-grid">
        ${entries.map(([key, value]) => {
          const label = this._humanizeKey(key);
          const formattedValue = this._formatValue(value);
          const valueClass = typeof value === 'boolean'
            ? (value ? 'bool-true' : 'bool-false')
            : '';

          return html`
            <div class="result-row">
              <span class="result-label">${label}</span>
              <span class="result-value ${valueClass}">${formattedValue}</span>
            </div>
          `;
        })}
      </div>
    `;
  }

  private _renderNarrativeContent(result: Record<string, any>) {
    const narrative = result.narrative ?? result.sarNarrative ?? 'No narrative available';

    return html`
      <blockquote class="narrative-block">${narrative}</blockquote>
    `;
  }

  private _humanizeKey(key: string): string {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, str => str.toUpperCase())
      .trim();
  }

  private _formatValue(value: any): string {
    if (value === null || value === undefined) {
      return 'N/A';
    }
    if (typeof value === 'boolean') {
      return value ? 'Yes' : 'No';
    }
    if (typeof value === 'number') {
      return value.toLocaleString();
    }
    if (typeof value === 'object') {
      return JSON.stringify(value, null, 2);
    }
    return String(value);
  }
}

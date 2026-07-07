import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { onPagesEvent, KeyboardShortcutMixin } from '@casehubio/blocks-ui-core';
import type { ColumnDef } from '@casehubio/blocks-ui-data-table';
import './case-list-pane.js';
import './case-detail-pane.js';

const DIVIDER_STORAGE_KEY = 'casehub-case-workbench-divider';

@customElement('case-workbench')
export class CaseWorkbench extends KeyboardShortcutMixin(LitElement) {
  @property() endpoint = '';
  @property({ type: Array }) columns: ColumnDef<any>[] = [];
  @property({ attribute: false }) getRowKey: (row: any) => string = () => '';
  @property({ attribute: false }) getRowClass?: (row: any) => string;
  @property() title = '';

  @state() private _selectedCaseId = '';
  @state() private _selectedCaseData: object | null = null;
  @state() private _dividerRatio = 0.4;
  @state() private _isDragging = false;

  private _unsubSelected?: () => void;
  private _unsubDeselected?: () => void;
  private _unsubRefresh?: () => void;

  static override styles = css`
    :host { display: block; height: 100%; width: 100%; overflow: hidden;
            font-family: var(--pages-font-family, system-ui); }
    .workbench { display: flex; flex-direction: column; height: 100%; }
    .header {
      padding: var(--pages-space-3, 12px) var(--pages-space-4, 16px);
      font-size: var(--pages-font-size-lg, 16px); font-weight: 600;
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
      color: var(--pages-neutral-11, #0a0a0a);
    }
    .split-pane { display: flex; flex: 1; overflow: hidden; }
    .left-panel {
      flex: 0 0 auto; min-width: 320px; max-width: 70%;
      border-right: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .divider {
      width: 4px; cursor: col-resize; flex-shrink: 0;
      background: var(--pages-neutral-3, #e5e5e5);
    }
    .divider:hover, .divider.dragging { background: var(--pages-accent-9, #3b82f6); }
    .right-panel { flex: 1; overflow: hidden; }

    @container (max-width: 768px) {
      .split-pane { flex-direction: column; }
      .left-panel { min-width: unset; max-width: unset; border-right: none;
                    border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4); height: 40%; }
      .divider { display: none; }
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    const saved = localStorage.getItem(DIVIDER_STORAGE_KEY);
    if (saved) this._dividerRatio = parseFloat(saved);

    this._unsubSelected = onPagesEvent(document, 'case:selected', (payload: any) => {
      this._selectedCaseId = payload.caseId;
      this._selectedCaseData = payload.caseData;
    });
    this._unsubDeselected = onPagesEvent(document, 'case:deselected', () => {
      this._selectedCaseId = '';
      this._selectedCaseData = null;
    });
    this._unsubRefresh = onPagesEvent(document, 'case:refresh', () => {
      this.shadowRoot?.querySelector('case-list-pane')?.refresh();
    });
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    this._unsubSelected?.();
    this._unsubDeselected?.();
    this._unsubRefresh?.();
  }

  private _onDividerDown = (e: PointerEvent) => {
    this._isDragging = true;
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };

  private _onDividerMove = (e: PointerEvent) => {
    if (!this._isDragging) return;
    const container = this.shadowRoot?.querySelector('.split-pane') as HTMLElement;
    if (!container) return;
    const rect = container.getBoundingClientRect();
    this._dividerRatio = Math.min(0.7, Math.max(0.2, (e.clientX - rect.left) / rect.width));
  };

  private _onDividerUp = () => {
    this._isDragging = false;
    localStorage.setItem(DIVIDER_STORAGE_KEY, String(this._dividerRatio));
  };

  override render() {
    return html`
      <div class="workbench" style="container-type: inline-size;">
        ${this.title ? html`<div class="header">${this.title}</div>` : ''}
        <div class="split-pane">
          <div class="left-panel" style="width: ${this._dividerRatio * 100}%">
            <case-list-pane
              .endpoint=${this.endpoint}
              .columns=${this.columns}
              .getRowKey=${this.getRowKey}
              .getRowClass=${this.getRowClass}
            ></case-list-pane>
          </div>
          <div class="divider ${this._isDragging ? 'dragging' : ''}"
               @pointerdown=${this._onDividerDown}
               @pointermove=${this._onDividerMove}
               @pointerup=${this._onDividerUp}
          ></div>
          <div class="right-panel">
            <case-detail-pane
              .caseId=${this._selectedCaseId}
              .caseData=${this._selectedCaseData}
            ></case-detail-pane>
          </div>
        </div>
      </div>
    `;
  }
}

import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { onPagesEvent } from '@casehubio/blocks-ui-core';
import { getCaseTabs, type CaseTabDefinition } from './tab-registry.js';

@customElement('case-detail-pane')
export class CaseDetailPane extends LitElement {
  @property() caseId = '';
  @property({ type: Object }) caseData: object | null = null;

  @state() private _activeTabId = '';

  private _unsubTabNav?: () => void;
  private _tabElements = new Map<string, HTMLElement>();

  static override styles = css`
    :host { display: flex; flex-direction: column; height: 100%; overflow: hidden; }
    .tab-bar {
      display: flex;
      gap: 0;
      border-bottom: 2px solid var(--pages-neutral-4, #d4d4d4);
      background: var(--pages-neutral-2, #f5f5f5);
      overflow-x: auto;
    }
    .tab {
      padding: var(--pages-space-2, 8px) var(--pages-space-4, 16px);
      cursor: pointer;
      font-size: var(--pages-font-size-sm, 13px);
      font-weight: 500;
      color: var(--pages-neutral-8, #404040);
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      white-space: nowrap;
      user-select: none;
    }
    .tab:hover { color: var(--pages-neutral-11, #0a0a0a); }
    .tab.active {
      color: var(--pages-accent-9, #3b82f6);
      border-bottom-color: var(--pages-accent-9, #3b82f6);
    }
    .tab-content { flex: 1; overflow: auto; padding: var(--pages-space-4, 16px); }
    .empty-state {
      display: flex; align-items: center; justify-content: center;
      height: 100%; color: var(--pages-neutral-7, #525252);
      font-size: var(--pages-font-size-lg, 16px);
    }
    .badge {
      margin-left: var(--pages-space-1, 4px);
      padding: 1px 6px;
      border-radius: 10px;
      font-size: 11px;
      background: var(--pages-accent-3, #dbeafe);
      color: var(--pages-accent-9, #3b82f6);
    }
  `;

  override connectedCallback(): void {
    super.connectedCallback();
    const tabs = getCaseTabs();
    if (tabs.length > 0 && !this._activeTabId) {
      this._activeTabId = tabs[0]!.id;
    }
    this._unsubTabNav = onPagesEvent(document, 'case:tab-navigate', (payload: any) => {
      if (payload?.tabId) this._activeTabId = payload.tabId;
    });
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    this._unsubTabNav?.();
  }

  override render() {
    if (!this.caseId) {
      return html`<div class="empty-state">Select an investigation to view details</div>`;
    }
    const tabs = getCaseTabs();
    return html`
      <div class="tab-bar">
        ${tabs.map(tab => {
          const badge = tab.badge?.(this.caseData ?? {});
          return html`
            <div class="tab ${tab.id === this._activeTabId ? 'active' : ''}"
                 @click=${() => { this._activeTabId = tab.id; }}>
              ${tab.label}
              ${badge != null ? html`<span class="badge">${badge}</span>` : nothing}
            </div>
          `;
        })}
      </div>
      <div class="tab-content">
        ${this._renderActiveTab()}
      </div>
    `;
  }

  private _renderActiveTab() {
    const tab = getCaseTabs().find(t => t.id === this._activeTabId);
    if (!tab) return html`<div class="empty-state">Tab not found</div>`;
    let el = this._tabElements.get(tab.id);
    if (!el) {
      el = document.createElement(tab.tagName);
      this._tabElements.set(tab.id, el);
    }
    (el as any).caseId = this.caseId;
    (el as any).caseData = this.caseData;
    return html`${el}`;
  }
}

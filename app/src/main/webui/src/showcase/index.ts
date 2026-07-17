/**
 * AML Workbench Showcase — standalone entry point.
 *
 * Installs the mock fetch interceptor BEFORE importing the app, so all
 * component data fetches hit mock data instead of a real backend.
 */

// Install mock fetch FIRST — before any component imports trigger data fetches
import './mock-fetch.js';

// Now import the real app — it will use mock fetch transparently
import '../index.js';

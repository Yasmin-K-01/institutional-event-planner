const LIVE_SYNC_ENDPOINT = 'https://institutional-event-planner.onrender.com/api/events/live-sync';
const HEADER_ROWS = 5;
const SUPPORTED_DEPARTMENT_SHEETS = [
  'AIML',
  'CSE',
  'ECE',
  'MECH',
  'CIVIL',
  'IT',
  'MBA',
  'MCA'
];

function onEdit(e) {
  scheduleLiveSync_();
}

function syncAllDepartmentEvents() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const events = [];

  spreadsheet.getSheets().forEach(sheet => {
    const department = sheet.getName().trim();
    if (!isDepartmentSheet_(department)) {
      return;
    }

    const lastRow = sheet.getLastRow();
    if (lastRow <= HEADER_ROWS) {
      return;
    }

    const rowCount = lastRow - HEADER_ROWS;
    const values = sheet.getRange(HEADER_ROWS + 1, 3, rowCount, 4).getValues();

    values.forEach(row => {
      const date = normalizeDate_(row[0]);
      const title = normalizeText_(row[1]);
      const type = normalizeText_(row[2]);
      const coordinator = normalizeText_(row[3]);

      if (!date && !title && !type && !coordinator) {
        return;
      }

      if (!date || !title) {
        console.warn(`Skipping incomplete row in ${department}: date and title are required.`);
        return;
      }

      events.push({
        department,
        date,
        title,
        type,
        coordinator
      });
    });
  });

  const response = UrlFetchApp.fetch(LIVE_SYNC_ENDPOINT, {
    method: 'post',
    contentType: 'application/json',
    payload: JSON.stringify(events),
    muteHttpExceptions: true
  });

  const status = response.getResponseCode();
  const body = response.getContentText();
  if (status < 200 || status >= 300) {
    throw new Error(`Live sync failed with HTTP ${status}: ${body}`);
  }

  console.log(`Live sync completed. Sent ${events.length} event(s). Server response: ${body}`);
}

function installLiveSyncTrigger() {
  ScriptApp.getProjectTriggers()
    .filter(trigger => trigger.getHandlerFunction() === 'handleInstalledEdit')
    .forEach(trigger => ScriptApp.deleteTrigger(trigger));

  ScriptApp.newTrigger('handleInstalledEdit')
    .forSpreadsheet(SpreadsheetApp.getActive())
    .onEdit()
    .create();
}

function handleInstalledEdit(e) {
  scheduleLiveSync_();
}

function scheduleLiveSync_() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) {
    return;
  }

  try {
    const cache = CacheService.getScriptCache();
    if (cache.get('live-sync-running')) {
      return;
    }
    cache.put('live-sync-running', 'true', 10);
    syncAllDepartmentEvents();
  } finally {
    lock.releaseLock();
  }
}

function isDepartmentSheet_(sheetName) {
  return SUPPORTED_DEPARTMENT_SHEETS.includes(sheetName.toUpperCase());
}

function normalizeDate_(value) {
  if (value instanceof Date) {
    return Utilities.formatDate(value, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  }
  return normalizeText_(value);
}

function normalizeText_(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

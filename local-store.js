/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
const RECORDS_KEY = 'nadoDiary.records.v1';
const TABLES = ['swim_settings', 'swim_sessions', 'swim_weight_entries', 'swim_extra_activities'];

function readRecords() {
    const raw = localStorage.getItem(RECORDS_KEY);
    const records = raw ? JSON.parse(raw) : {};
    for (const table of TABLES) {
        if (!Array.isArray(records[table])) records[table] = [];
    }
    return records;
}

function createLocalStore() {
    class Query {
        constructor(table) {
            if (!TABLES.includes(table)) throw new Error('Tipo de registro inválido.');
            this.table = table;
            this.filters = [];
            this.method = 'read';
        }
        select() { return this; }
        eq(key, value) { this.filters.push(row => row[key] === value); return this; }
        order(key, options = {}) { this.sortKey = key; this.direction = options.ascending === false ? -1 : 1; return this; }
        insert(payload) { this.method = 'insert'; this.payload = payload; return this; }
        upsert(payload) { this.method = 'upsert'; this.payload = payload; return this; }
        delete() { this.method = 'delete'; return this; }
        single() { this.one = true; return this.execute(); }
        maybeSingle() { this.one = true; return this.execute(); }
        then(resolve, reject) { return this.execute().then(resolve, reject); }
        async execute() {
            try {
                const db = readRecords();
                let rows = db[this.table].filter(row => this.filters.every(filter => filter(row)));
                if (this.method === 'delete') {
                    db[this.table] = db[this.table].filter(row => !rows.includes(row));
                } else if (this.method !== 'read') {
                    const values = Array.isArray(this.payload) ? this.payload : [this.payload];
                    rows = values.map(value => ({...value, id: value.id || crypto.randomUUID()}));
                    for (const row of rows) {
                        const index = this.method === 'upsert' ? db[this.table].findIndex(old => old.user_id === row.user_id) : -1;
                        if (index >= 0) db[this.table][index] = row;
                        else db[this.table].push(row);
                    }
                }
                if (this.method !== 'read') localStorage.setItem(RECORDS_KEY, JSON.stringify(db));
                if (this.sortKey) rows.sort((a, b) => String(a[this.sortKey] || '').localeCompare(String(b[this.sortKey] || '')) * this.direction);
                return {data: this.one ? rows[0] || null : rows, error: null};
            } catch (error) {
                return {data: null, error: {message: 'Não foi possível acessar o armazenamento do aparelho. ' + error.message}};
            }
        }
    }
    return {from: table => new Query(table)};
}

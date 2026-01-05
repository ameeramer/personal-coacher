#!/usr/bin/env node

/**
 * Generate VAPID keys for Web Push notifications
 *
 * Run this script once to generate your VAPID keys:
 * node scripts/generate-vapid-keys.js
 *
 * Then add the output to your .env file.
 */

import webpush from 'web-push';

const vapidKeys = webpush.generateVAPIDKeys();

console.log('VAPID Keys Generated!');
console.log('');
console.log('Add these to your .env file:');
console.log('');
console.log(`NEXT_PUBLIC_VAPID_PUBLIC_KEY=${vapidKeys.publicKey}`);
console.log(`VAPID_PRIVATE_KEY=${vapidKeys.privateKey}`);
console.log(`VAPID_SUBJECT=mailto:your-email@example.com`);
console.log('');
console.log('Note: Replace your-email@example.com with your actual email address.');

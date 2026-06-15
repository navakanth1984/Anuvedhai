import React from 'react';
import { StyleSheet, View, Text, TouchableOpacity, Platform } from 'react-native';

interface NativeHeaderProps {
  onRefresh: () => void;
  onCopyLink: () => void;
  currentUrl: string;
}

export const NativeHeader: React.FC<NativeHeaderProps> = ({ onRefresh, onCopyLink, currentUrl }) => {
  // Format URL for elegant display (only show hostname or last section)
  const displayUrl = currentUrl.replace('https://', '').substring(0, 30) + (currentUrl.length > 30 ? '...' : '');

  return (
    <View style={styles.headerContainer}>
      <View style={styles.branding}>
        <Text style={styles.badge}>ANUVEDHAI</Text>
        <Text style={styles.urlText}>{displayUrl}</Text>
      </View>

      <View style={styles.controls}>
        <TouchableOpacity style={styles.iconButton} onPress={onCopyLink} activeOpacity={0.7}>
          <Text style={styles.buttonText}>🔗 Copy</Text>
        </TouchableOpacity>
        
        <View style={styles.divider} />

        <TouchableOpacity style={styles.iconButton} onPress={onRefresh} activeOpacity={0.7}>
          <Text style={styles.buttonText}>🔄 Reload</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  headerContainer: {
    height: 52,
    backgroundColor: '#1E1E1E',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.2,
        shadowRadius: 2,
      },
      android: {
        elevation: 4,
      },
    }),
  },
  branding: {
    flex: 1,
    marginRight: 10,
  },
  badge: {
    color: '#FF9800',
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.5,
  },
  urlText: {
    color: '#A0A0A0',
    fontSize: 10,
    marginTop: 2,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2A2A2A',
    borderRadius: 16,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  iconButton: {
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '700',
  },
  divider: {
    width: 1,
    height: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    marginHorizontal: 8,
  },
});

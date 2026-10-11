export default {
  branches: ['main'],
  tagFormat: 'v${version}',
  plugins: [
    '@semantic-release/commit-analyzer',
    '@semantic-release/release-notes-generator',
    ['@semantic-release/exec', {
      prepareCmd: 'bash scripts/Prepare-GoldBagRelease.sh ${nextRelease.version}',
    }],
    ['@semantic-release/github', {
      assets: ['dist/GoldBag-*.jar', 'dist/GoldBag-*.jar.sha256'],
      successComment: false,
      failComment: false,
      releasedLabels: false,
    }],
  ],
};

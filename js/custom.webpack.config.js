// noinspection NpmUsedModulesInstalled,JSUnresolvedFunction,JSUnresolvedVariable

const {merge} = require('webpack-merge');
// noinspection JSFileReferences
const generated = require('./scalajs.webpack.config');

const local = {
    module: {
        rules: [
            {
                test: /\.less$/,
                use: [
                    {loader: "style-loader"},
                    {loader: "css-loader"},
                    {loader: 'less-loader', options: {lessOptions: {javascriptEnabled: true}}}
                ]
            },
            {test: /\.css$/, use: ['style-loader', 'css-loader']},
            {test: /\.(ttf|eot|woff|png|glb|svg)$/, use: 'file-loader'},
            {test: /\.(eot)$/, use: 'url-loader'}
        ]
    },

    devServer: {
        proxy: {
            '/api': {
                target: 'http://localhost:9090',
                pathRewrite: {'^/api': ''}
            }
        }
    }
};

module.exports = merge(generated, local);
